
package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;


public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener{
  boolean all;
  boolean status;
  int[] id = new int[]{R.id.limit20, R.id.limit30, R.id.limit50, R.id.limit60, R.id.limit70, R.id.limit80, R.id.restrEnds80,
          R.id.limit100, R.id.limit120, R.id.noOvertaking, R.id.noOvertakingTrucks, R.id.priorityNextIntersection, R.id.priorityRoad,
          R.id.giveWay, R.id.stop, R.id.noTrafficBothWays, R.id.noTrucks, R.id.noEntry, R.id.danger,
          R.id.bendLeft, R.id.bendRight, R.id.bend, R.id.unevenRoad, R.id.slipperyRoad, R.id.roadNarrows,
          R.id.construction, R.id.trafficSignal, R.id.pedestrianCrossing, R.id.schoolCrossing, R.id.cyclesCrossing, R.id.snow,
          R.id.animals, R.id.restrictionEnds, R.id.goRight, R.id.goLeft, R.id.goStraight, R.id.goRightStraight,
          R.id.goLeftStraight, R.id.keepRight, R.id.keepLeft, R.id.roundabout, R.id.restrictionEndsOvertaking, R.id.restrictionEndsOvertkTrucks};

  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;
  private TextToSpeech textToSpeech;
  protected static HashMap<String, Boolean> labelsDetected = new HashMap<String, Boolean>() {{
    put("limit20", true);
    put("limit30", true);
    put("limit50", true);
    put("limit60", true);
    put("limit70", true);
    put("limit80", true);
    put("restrEnds80", true);
    put("limit100", true);
    put("limit120", true);
    put("noOvertaking", true);
    put("noOvertakingTrucks", true);
    put("priorityNextIntersection", true);
    put("priorityRoad", true);
    put("giveWay", true);
    put("stop", true);
    put("noTrafficBothWays", true);
    put("noTrucks", true);
    put("noEntry", true);
    put("danger", true);
    put("bendLeft", true);
    put("bendRight", true);
    put("bend", true);
    put("unevenRoad", true);
    put("slipperyRoad", true);
    put("roadNarrows", true);
    put("construction", true);
    put("trafficSignal", true);
    put("pedestrianCrossing", true);
    put("schoolCrossing", true);
    put("cyclesCrossing", true);
    put("snow", true);
    put("animals", true);
    put("restrictionEnds", true);
    put("goRight", true);
    put("goLeft", true);
    put("goStraight", true);
    put("goRightStraight", true);
    put("goLeftStraight", true);
    put("keepRight", true);
    put("keepLeft", true);
    put("roundabout", true);
    put("restrictionEndsOvertaking", true);
    put("restrictionEndsOvertkTrucks", true);
  }};

  FirebaseFirestore db;
  LocationTrack locationTrack;
  private ArrayList permissionsToRequest;
  private ArrayList permissionsRejected = new ArrayList();
  private ArrayList permissions = new ArrayList();

  private final static int ALL_PERMISSIONS_RESULT = 101;
    @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_camera);
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    db = FirebaseFirestore.getInstance();

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }
      this.textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
          @Override
          public void onInit(int status) {
              if (status == TextToSpeech.SUCCESS) {
                  LOGGER.i("onCreate", "TextToSpeech is initialised");
              } else {
                  LOGGER.e("onCreate", "Cannot initialise text to speech!");
              }
          }
      });
  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
          }
        };

    postInferenceCallback =
        new Runnable() {
          @Override
          public void run() {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
          }
        };
    processImage();
  }

  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };

      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  private boolean isHardwareLevelSupported(
      CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        useCamera2API =
            (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
          CameraConnectionFragment.newInstance(
              new CameraConnectionFragment.ConnectionCallback() {
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {
                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),
              getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

    private List<Classifier.Recognition> currentRecognitions;

    protected void toSpeech(List<Classifier.Recognition> recognitions) {
        if (recognitions.isEmpty() || textToSpeech.isSpeaking()) {
            currentRecognitions = Collections.emptyList();
            return;
        }

        if (currentRecognitions != null) {

            if (currentRecognitions.equals(recognitions)) {
                return;
            }
            final Set<Classifier.Recognition> intersection = new HashSet<>(recognitions);
            intersection.retainAll(currentRecognitions);

            if (intersection.equals(recognitions)) {
                return;
            }
        }

        currentRecognitions = recognitions;

        speak();
    }

    private void speak() {

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < currentRecognitions.size(); i++) {
            Classifier.Recognition recognition = currentRecognitions.get(i);
            stringBuilder.append(recognition.getTitle());

            if (i + 1 < currentRecognitions.size()) {
                stringBuilder.append(" and ");
            }
        }
        stringBuilder.append(" detected.");

        textToSpeech.speak(stringBuilder.toString(), TextToSpeech.QUEUE_FLUSH, null);
    }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
      if(item.getItemId() == R.id.chooseAll) {
        all = true;
        if (item.isChecked()){
          status = !item.isChecked();
          item.setChecked(false);
        } else {
          status = !item.isChecked();
          item.setChecked(true);
        }
        for (int i = 0; i < 43; i++) {
          labelsDetected.put(getResources().getResourceEntryName(id[i]), status);
        }
      }
      else if(item.isChecked()){
        item.setChecked(false);
        labelsDetected.put(getResources().getResourceEntryName(item.getItemId()), false);
      } else {
        item.setChecked(true);
        labelsDetected.put(getResources().getResourceEntryName(item.getItemId()), true);
      }
      return true;
    }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    if (all){
      all = false;
      for (int i = 0; i < 43; i++) {
        menu.findItem(id[i]).setChecked(status);
        labelsDetected.put(getResources().getResourceEntryName(id[i]), status);
      }
    }
    return true;
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

}
