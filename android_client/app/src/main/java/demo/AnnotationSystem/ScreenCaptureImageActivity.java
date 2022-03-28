package demo.AnnotationSystem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import demo.AnnotationSystem.Utilities.*;
import kotlin.Pair;

import static demo.AnnotationSystem.Utilities.UtilityKt.BUFFER_LOCK;
import static java.lang.Math.round;

public class ScreenCaptureImageActivity extends Activity {

    static boolean active = false;

    private static final String TAG = ScreenCaptureImageActivity.class.getName();
    private static final int REQUEST_CODE = 100;
    private static String STORE_DIRECTORY;
    private static final String SCREENCAP_NAME = "screencap";
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static MediaProjection sMediaProjection;

    private MediaProjectionManager mProjectionManager;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private OrientationChangeCallback mOrientationChangeCallback;
    private long lastTime = 0;


    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private ByteBuffer cloneByteBuffer(final ByteBuffer original) {
            // Create clone with same capacity as original.
            final ByteBuffer clone = (original.isDirect()) ?
                    ByteBuffer.allocateDirect(original.capacity()) :
                    ByteBuffer.allocate(original.capacity());

            // Create a read-only copy of the original.
            // This allows reading from the original without modifying it.
            final ByteBuffer readOnlyCopy = original.asReadOnlyBuffer();

            // Flip and read from the original.
            readOnlyCopy.flip();
            clone.put(readOnlyCopy);

            clone.position(original.position());
            clone.limit(original.limit());
            clone.order(original.order());
            return clone;
        }

        private boolean isBoundLegal(Rect bounds, Bitmap bitmap) {
            return (bounds.top + bounds.height() <= bitmap.getHeight() && bounds.left + bounds.width() <= bitmap.getWidth()
                    && bounds.left >= 0 && bounds.top >= 0);
        }

        @Override
        public void onImageAvailable(ImageReader reader) {

            Image image = null;

            long currTime = System.currentTimeMillis();

            try {
                image = reader.acquireLatestImage();
                if (
                        image != null
                        && image.getPlanes() != null
                        && (currTime - lastTime >= 50)
                        && UtilityKt.getGShouldStartBuffer()
                ) {

                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;

                    // create bitmap
                    Bitmap bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    Rect cropBounds = UtilityKt.getGCropBounds();
                    Bitmap bitmapSmall = null;
                    if (isBoundLegal(cropBounds, bitmap)) {
                        Bitmap cropped = Bitmap.createBitmap(bitmap, cropBounds.left, cropBounds.top, cropBounds.width(), cropBounds.height());

                        bitmapSmall = Bitmap.createScaledBitmap(
                                cropped,
                                (int) (round(cropped.getWidth() / UtilityKt.SCALE_FACTOR)),
                                (int) (round(cropped.getHeight() / UtilityKt.SCALE_FACTOR)),
                                true
                        );

                        bitmap.recycle();
                        cropped.recycle();
                    }
                    // set full size image; not used
                /* Bitmap fullSize = UtilityKt.getGFullSizeImage();
                if (fullSize != null) {
                    fullSize.recycle();
                    UtilityKt.setGFullSizeImage(bitmap);
                } */

                    final FrameData frameData = new FrameData(bitmapSmall, currTime);

                    synchronized (BUFFER_LOCK) {
                        long deltaT = 0L;
                        LinkedList<FrameData> gImageBuffer = UtilityKt.getGImageBuffer();
                        LinkedList<Pair<Long, ByteArrayOutputStream>> gImageByteStream = UtilityKt.getGImageByteStream();
                        if (gImageBuffer.size() > 0) {
                            deltaT = currTime - gImageBuffer.peek().getTimestamp();
                        }
                        while (deltaT > UtilityKt.BUFFER_LENGTH && gImageBuffer.size() > 0) {
                            FrameData oldPair = gImageBuffer.poll();
                            UtilityKt.setGOldBufferPair(oldPair);
                            if (gImageBuffer.size() > 0) {
                                deltaT = currTime - gImageBuffer.peek().getTimestamp();
                            } else {
                                break;
                            }
                        }
                        gImageBuffer.add(frameData);

                        if (currTime - lastTime > 1) {
                            Log.d(TAG, "t = " + currTime + " - images bufferSize = " + gImageBuffer.size());
                        }
                        lastTime = currTime;

                        UtilityKt.saveImg(frameData, UtilityKt.getGImageBuffer().size() - 1);
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private class OrientationChangeCallback extends OrientationEventListener {

        OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                mRotation = rotation;
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e("ScreenCapture", "stopping projection.");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    if (mOrientationChangeCallback != null) mOrientationChangeCallback.disable();
                    sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }

    /****************************************** Activity Lifecycle methods ************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screencapture);
        mHandler = new Handler();

        // call for the projection manager
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        Button accessibilityBtn = (Button) this.findViewById(R.id.activateAccessibilityButton2);
        accessibilityBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        Button stopBtn = (Button) this.findViewById(R.id.stop);
        stopBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        startProjection();
    }

    @Override
    protected void onDestroy() {
        stopProjection();
        active = false;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);

            if (sMediaProjection != null) {
                File externalFilesDir = getExternalFilesDir(null);
                if (externalFilesDir != null) {
                    STORE_DIRECTORY = externalFilesDir.getAbsolutePath() + "/screenshots/";
                    File storeDirectory = new File(STORE_DIRECTORY);
                    if (!storeDirectory.exists()) {
                        boolean success = storeDirectory.mkdirs();
                        if (!success) {
                            Log.e(TAG, "failed to create file storage directory.");
                            return;
                        }
                    }
                } else {
                    Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.");
                    return;
                }

                // display metrics
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                mDensity = metrics.densityDpi;
                mDisplay = getWindowManager().getDefaultDisplay();

                // create virtual display depending on device width / height
                createVirtualDisplay();

                // register orientation change callback
                mOrientationChangeCallback = new OrientationChangeCallback(this);
                if (mOrientationChangeCallback.canDetectOrientation()) {
                    mOrientationChangeCallback.enable();
                }

                // register media projection stop callback
                sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);

                active = true;
            } else {
                finish();
            }
        }
    }

    /****************************************** UI Widget Callbacks *******************************/
    private void startProjection() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sMediaProjection != null) {
                    sMediaProjection.stop();
                }
            }
        });
    }

    /****************************************** Virtual Display creation **************************/
    private void createVirtualDisplay() {
        // get width and height
        Point size = new Point();
        mDisplay.getRealSize(size);
        mWidth = size.x;
        mHeight = size.y;

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = sMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }
}