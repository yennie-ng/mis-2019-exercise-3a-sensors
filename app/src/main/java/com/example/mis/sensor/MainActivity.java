package com.example.mis.sensor;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private double[] freqCounts;

    GraphView graphAcceleration;
    GraphView graphF;

    TextView statusText;

    private LineGraphSeries<DataPoint> seriesX;
    private LineGraphSeries<DataPoint> seriesY;
    private LineGraphSeries<DataPoint> seriesZ;
    private LineGraphSeries<DataPoint> seriesM;
    private LineGraphSeries<DataPoint> fftSeries;

    private Queue<Double> xCalculateQueue = new LinkedList<>();

    private int winSize = 32;
    private int graphRange = 500;
    private int currentX = 0;
    private boolean isFastestSensor = false;
    private long lastUpdate;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean startSensor = false;
    private double lastMagnitude = 10;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Objects.requireNonNull(getSupportActionBar()).hide();
        }

        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.textStatus);
        seekBarRegister();
        prepareSensor();
        prepareGraph();
        lastUpdate = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
            if (!startSensor)
                startSensor = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @SuppressLint("StaticFieldLeak")
    private class FFTAsynctask extends AsyncTask<double[], Void, double[]> {

        private int wsize; //window size must be power of 2

        // constructor to set window size
        FFTAsynctask(int wdsize) {
            this.wsize = wdsize;
        }

        @Override
        protected double[] doInBackground(double[]... values) {

            double[] realPart = values[0].clone(); // actual acceleration values
            double[] imgPart = new double[wsize]; // init empty

            FFT fft = new FFT(wsize);
            fft.fft(realPart, imgPart);
            //init new double array for magnitude (e.g. frequency count)
            double[] magnitude = new double[wsize];

            //fill array with magnitude values of the distribution
            for (int i = 0; wsize > i ; i++) {
                magnitude[i] = Math.sqrt(Math.pow(realPart[i], 2) + Math.pow(imgPart[i], 2));
            }
            return magnitude;
        }

        @Override
        protected void onPostExecute(double[] values) {
            freqCounts = values;
            drawFFTGraph();
        }
    }

    private void drawFFTGraph() throws ArrayIndexOutOfBoundsException, NullPointerException {
        int currentXAxis = 0;
        DataPoint[] list = new DataPoint[winSize];
        for (int index = 0; index < freqCounts.length; index++) {

            DataPoint point = new DataPoint(currentXAxis, freqCounts[index]);
            list[index] = point;
            currentXAxis += 5;
        }
        fftSeries = new LineGraphSeries<>(list);
        graphF.removeAllSeries();
        graphF.addSeries(fftSeries);
    }

    private void seekBarRegister() {
        //------init
        SeekBar rateBar = findViewById(R.id.rateBar);
        SeekBar sizeBar = findViewById(R.id.wSizeBar);

        //------configuration
        sizeBar.setProgress(0);
        sizeBar.setMax(5); // 5 is enough until 1024, too much will crash (cause background task)
        rateBar.setProgress(0);
        rateBar.setMax(3);

        //------ listener
        rateBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int delay = 0;
                switch (seekBar.getProgress()) {
                    case 0:
                        delay = SensorManager.SENSOR_DELAY_NORMAL;
                        isFastestSensor = false;
                        break;
                    case 1:
                        delay = SensorManager.SENSOR_DELAY_UI;
                        isFastestSensor = false;
                        break;
                    case 2:
                        delay = SensorManager.SENSOR_DELAY_GAME;
                        isFastestSensor = false;
                        break;
                    case 3:
                        delay = SensorManager.SENSOR_DELAY_FASTEST;
                        isFastestSensor = true;
                        break;
                }
                refreshSensor(delay);
            }
        });

        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int size = 0;
                switch (seekBar.getProgress()) {
                    case 0:
                        size = 32;
                        break;
                    case 1:
                        size = 64;
                        break;
                    case 2:
                        size = 128;
                        break;
                    case 3:
                        size = 256;
                        break;
                    case 4:
                        size = 512;
                        break;
                    case 5:
                        size = 1024;
                        break;
                }
                xCalculateQueue = new LinkedList<>();
                winSize = size;
                graphF.removeAllSeries();
                graphF.getViewport().setMaxX(winSize);
            }
        });
    }

    private void getAccelerometer(SensorEvent event) {

        float[] values = event.values;

        double x = values[0];
        double y = values[1];
        double z = values[2];

        if (!startSensor) {
            lastX = x;
            lastY = y;
            lastZ = z;
        }

        long currentTime = System.currentTimeMillis();
        long time = 50;
        if ((currentTime - lastUpdate) % time == 0) {         //update every 50ms
            lastUpdate = currentTime;
            float speedMovement = (float) (Math.abs(x + y + z - lastX - lastY - lastZ) / time * 10000);
            if (speedMovement > 800) {
                statusText.setText("Shaking");
            } else {
                statusText.setText("nothing");
            }
            lastX = x;
            lastY = y;
            lastZ = z;
        }

        double magnitude = Math.sqrt(x * x + y * y + z * z);

        if (!startSensor) {
            lastMagnitude = magnitude;
        }

        accelerationUpdate(x,y,z,magnitude);

        xCalculateQueue.add(magnitude);

        if (xCalculateQueue.size() > winSize) {
            xCalculateQueue.remove();
        }

        // too fast sensor may lead the async task will have input array longer than execute post input
        if (currentX % ((isFastestSensor) ? 500 : 150) == 0) {
            int index = 0;
            double[] input = new double[winSize];
            for (Double element : xCalculateQueue) {
                input[index] = element;
                index++;
            }
            new FFTAsynctask(winSize).execute(input);
        }
        currentX += 5;

    }


    private void prepareSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager != null ?
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) :
                null;
    }

    private void prepareGraph() {
        //--------------- graph init

        graphAcceleration = findViewById(R.id.graphX);
        graphF = findViewById(R.id.fftGraph);

        seriesX = new LineGraphSeries<>();
        initSeries(seriesX,Color.RED);

        seriesY = new LineGraphSeries<>();
        initSeries(seriesY,Color.GREEN);

        seriesZ = new LineGraphSeries<>();
        initSeries(seriesZ,Color.CYAN);

        seriesM = new LineGraphSeries<>();
        initSeries(seriesM,Color.MAGENTA);

        fftSeries = new LineGraphSeries<>();
        initSeries(fftSeries,Color.BLUE);

        setStyleOf(graphAcceleration,"x : red, y: green, z: cyan, magnitude: magenta",
                -20,60,seriesX,graphRange);
        setStyleOf(graphAcceleration,"x : red, y: green, z: cyan, magnitude: magenta",
                -20,60,seriesY,graphRange);
        setStyleOf(graphAcceleration,"x : red, y: green, z: cyan, magnitude: magenta",
                -20,60,seriesZ,graphRange);
        setStyleOf(graphAcceleration,"x : red, y: green, z: cyan, magnitude: magenta",
                -20,60,seriesM,graphRange);
        setStyleOf(graphF,"FFT",0,200,fftSeries,winSize);

    }

    private void initSeries(LineGraphSeries<DataPoint> series, int color) {
        series.setColor(color);
        series.setDataPointsRadius(10);
    }

    private void setStyleOf(GraphView graph,
                            String name,
                            double minY,
                            double maxY,
                            LineGraphSeries<DataPoint> source,
                            int range) {
        graph.addSeries(source);
        graph.setTitle(name);
        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
        graph.getGridLabelRenderer().setHorizontalLabelsColor(Color.WHITE);

        Viewport viewPort = graph.getViewport();
        viewPort.setScrollable(false);
        viewPort.setScalable(false);

        viewPort.setXAxisBoundsManual(true);
        viewPort.setMinX(0);
        viewPort.setMaxX(range);

        viewPort.setYAxisBoundsManual(true);
        viewPort.setMinY(minY);
        viewPort.setMaxY(maxY);
    }

    private void accelerationUpdate(final double xAxis,
                                    final double yAxis,
                                    final double zAxis,
                                    final double mag) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seriesX.appendData(new DataPoint(currentX, xAxis),true,graphRange);
                seriesY.appendData(new DataPoint(currentX, yAxis),true,graphRange);
                seriesZ.appendData(new DataPoint(currentX, zAxis),true,graphRange);
                seriesM.appendData(new DataPoint(currentX, mag),true,graphRange);
            }
        });
    }

    private void refreshSensor(int delay) {
        sensorManager.unregisterListener(this);
        sensorManager.registerListener(this,accelerometer,delay);
    }

}

