package com.walletmix.biometrictest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatButton;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import SecuGen.FDxSDKPro.JSGFPLib;
import SecuGen.FDxSDKPro.SGFDxDeviceName;
import SecuGen.FDxSDKPro.SGFDxErrorCode;
import SecuGen.FDxSDKPro.SGFDxSecurityLevel;
import SecuGen.FDxSDKPro.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.SGFingerInfo;
import SecuGen.FDxSDKPro.SGFingerPosition;

public class BiometricsActivity extends AppCompatActivity implements View.OnClickListener {

    private AppCompatButton btnTakeFingerPrint;
    private AppCompatButton btnRegisterFinger;
    private AppCompatButton btnMatchFinger;
    private ImageView imgFingerPrint;
    private static final String TAG = "SecuGen USB";

    // Image Info >>>>>
    private int mImageWidth;
    private int mImageHeight;

    // Images >>>>
    private byte[] mFingerPrintImage;
    private byte[] mRegisterImage;
    private byte[] mVerifyImage;

    // Template >>>>>
    private byte[] mRegisterTemplate;
    private byte[] mVerifyTemplate;

    // Pending Intent >>>>>
    private PendingIntent mPermissionIntent;
    private IntentFilter intentFilter;

    private static final String ACTION_USB_PERMISSION = "ccom.walletmix.biometrictest.USB_PERMISSION";
    private final int TIME_OUT = 10000;
    private final int IMAGE_QUALITY = 80;

    // JSGFLib >>>
    private JSGFPLib sgfplib;

    private AlertDialog alertDialog;
    private boolean isUsbReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometrics);
        onViewReady();
    }

    private void onViewReady() {
        btnTakeFingerPrint = findViewById(R.id.btnTakeFingerPrint);
        btnRegisterFinger = findViewById(R.id.btnRegister);
        btnMatchFinger = findViewById(R.id.btnMatch);
        imgFingerPrint = findViewById(R.id.imgFingerPrint);

        btnTakeFingerPrint.setOnClickListener(this);
        btnRegisterFinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerFingerPrint();
            }
        });
        btnMatchFinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                matchFingerPrint();
            }
        });

        // Initially Button is disabled. It will enabled if permission is granted
        btnTakeFingerPrint.setEnabled(false);

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        sgfplib = new JSGFPLib((UsbManager) getSystemService(Context.USB_SERVICE));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isUsbReceiverRegistered) {
            registerReceiver(mUsbReceiver, intentFilter);
            isUsbReceiverRegistered = true;
        }
        initFingerPrintDevice();
    }

    private void initFingerPrintDevice() {
        long error = sgfplib.Init(SGFDxDeviceName.SG_DEV_AUTO);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE) { // IF get Error
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND) { // If error type device not found
                dialogBuilder.setTitle("No Device Found !");
                dialogBuilder.setMessage("No fingerprint device found. Please connect a fingerprint device.");
            } else {
                dialogBuilder.setTitle("Initialization Failed !");
                dialogBuilder.setMessage("Fingerprint device initialization failed!");
            }
            dialogBuilder.setPositiveButton("EXIT",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            finish();
                        }
                    }
            );
            dialogBuilder.setCancelable(false);
            alertDialog = dialogBuilder.create();
            alertDialog.show();
        } else {
            UsbDevice usbDevice = sgfplib.GetUsbDevice(); // If Init done Now Get Device
            if (usbDevice == null) {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                dialogBuilder.setMessage("SDU04P or SDU03P fingerprint sensor not found!");
                dialogBuilder.setTitle("SecuGen Fingerprint SDK");
                dialogBuilder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                finish();
                            }
                        }
                );
                dialogBuilder.setCancelable(false);
                alertDialog = dialogBuilder.create();
                alertDialog.show();
            } else {
                sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
            }
        }
    }

    @Override
    protected void onPause() {
        if (isUsbReceiverRegistered) {
            unregisterReceiver(mUsbReceiver);
            isUsbReceiverRegistered = false;
        }
        sgfplib.CloseDevice();
        super.onPause();
    }


    @Override
    public void onDestroy() {
        sgfplib.Close();
        mFingerPrintImage = null;
        mRegisterImage = null;
        mVerifyImage = null;
        mRegisterTemplate = null;
        mVerifyTemplate = null;
        imgFingerPrint = null;
        super.onDestroy();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnTakeFingerPrint: {
                captureFingerPrint();
            }
        }
    }

    private void registerFingerPrint() {
        if (mRegisterImage != null)
            mRegisterImage = null;

        // Taking Finger Print Image >>>
        mRegisterImage = new byte[mImageWidth * mImageHeight];
        //sgfplib.GetImage(mRegisterImage);
        sgfplib.GetImageEx(mRegisterImage, TIME_OUT, IMAGE_QUALITY);
        imgFingerPrint.setImageBitmap(ImageConverter.toGrayscale(mRegisterImage, mImageWidth, mImageHeight));
        int[] imgQuality = new int[1];
        sgfplib.GetImageQuality(mImageWidth, mImageHeight, mRegisterImage, imgQuality);

        SGFingerInfo fingerInfo = new SGFingerInfo();
        fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_LI;
        fingerInfo.ImageQuality = imgQuality[0];
        fingerInfo.ViewNumber = 1;

        sgfplib.CreateTemplate(fingerInfo, mRegisterImage, mRegisterTemplate);
        Toast.makeText(this, "" + imgQuality[0], Toast.LENGTH_SHORT).show();
    }


    private void matchFingerPrint() {
        if (mVerifyImage != null)
            mVerifyImage = null;

        // Taking Verify Image >>>>>>
        mVerifyImage = new byte[mImageWidth * mImageHeight];
        //sgfplib.GetImage(mVerifyImage); // Capture Image instantly clicked the button without checking the presence of a fingerprint
        sgfplib.GetImageEx(mVerifyImage, TIME_OUT, IMAGE_QUALITY); // Check the presence of finger and capture image. If no finger found in device until timeout.
        imgFingerPrint.setImageBitmap(ImageConverter.toGrayscale(mVerifyImage, mImageWidth, mImageHeight));
        int[] imgQuality = new int[1];
        sgfplib.GetImageQuality(mImageWidth, mImageHeight, mVerifyImage, imgQuality);

        SGFingerInfo fingerInfo = new SGFingerInfo();
        fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_LI;
        fingerInfo.ImageQuality = imgQuality[0];
        fingerInfo.ViewNumber = 1;

        sgfplib.CreateTemplate(fingerInfo, mVerifyImage, mVerifyTemplate);

        // Matching >>>>
        boolean[] matched = new boolean[1];
        sgfplib.MatchTemplate(mRegisterTemplate, mVerifyTemplate, SGFDxSecurityLevel.SL_NORMAL, matched);
        if (matched[0]) {
            Toast.makeText(this, "Matched", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Not Matched", Toast.LENGTH_SHORT).show();
        }
    }

    private void captureFingerPrint() {
        mFingerPrintImage = new byte[mImageWidth * mImageHeight];
        sgfplib.GetImageEx(mFingerPrintImage, 10000, 70);
        imgFingerPrint.setImageBitmap(ImageConverter.toGrayscale(mFingerPrintImage, mImageWidth, mImageHeight));
        mFingerPrintImage = null;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            /**
                             * If only one USB fingerprint reader is connected to the PC, devId will be 0. If multiple USB fingerprint readers are
                             * connected to one PC, devId can range from 0 to 9. The maximum number of SecuGen USB readers that can be
                             * connected to one PC is 10. In general, if only one USB reader is connected to the PC,
                             * then 0 or USB_AUTO_DETECT is recommended.
                             * */
                            sgfplib.OpenDevice(0); // Opening Device
                            SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
                            sgfplib.GetDeviceInfo(deviceInfo); // Getting Device Information
                            mImageWidth = deviceInfo.imageWidth;
                            mImageHeight = deviceInfo.imageHeight;

                            sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
                            int[] mMaxTemplateSize = new int[1];
                            sgfplib.GetMaxTemplateSize(mMaxTemplateSize);
                            mRegisterTemplate = new byte[mMaxTemplateSize[0]];
                            mVerifyTemplate = new byte[mMaxTemplateSize[0]];

                            btnTakeFingerPrint.setEnabled(true);
                        } else {
                            Toast.makeText(BiometricsActivity.this, "Device is Null", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BiometricsActivity.this, "Permission denied for device " + device, Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                if (alertDialog != null && alertDialog.isShowing())
                    alertDialog.dismiss();
                initFingerPrintDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                btnTakeFingerPrint.setEnabled(false);
                Toast.makeText(BiometricsActivity.this, "Fingerprint Device Removed", Toast.LENGTH_SHORT).show();
            }
        }
    };
}
/*** GetImage() VS GetImageEx() >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
 * GetImage() captures an image without checking for the presence of a finger or checking image quality.
 *
 * GetImageEx() captures fingerprint images continuously, checks the image quality against a specified quality
 * value and ignores the image if it does not contain a fingerprint or if the quality of the fingerprint is not acceptable. If a
 * quality image is captured within the given time (the second parameter), SGFPM_GetImageEx() ends its processing.
 * */


/*** REGISTRATION >>>>>>>>>>
 * It is recommended to capture at least two image samples per fingerprint for a higher degree of accuracy.
 * The minutiae data from each image can then be compared against each other (i.e. matched) to confirm the
 * quality of the registered fingerprints.
 * **/


/** Android Device Requirements >>>>
 *
 *  ARM based Android tablet or smart phone
 *  USB host controller on device
 *  Standard USB port or Micro-USB to USB OTG adapter
 *  Android OS Version 3.1 (Honeycomb) or later
 *
 * */