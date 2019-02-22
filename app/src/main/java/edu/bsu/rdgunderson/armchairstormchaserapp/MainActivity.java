package edu.bsu.rdgunderson.armchairstormchaserapp;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.mapbox.android.gestures.StandardScaleGestureDetector;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private static final int PLACE_SELECTION_REQUEST_CODE = 56789;
    private static final String MARKER_SOURCE = "markers-source";
    private static final String MARKER_STYLE_LAYER = "markers-style-layer";
    private static final String MARKER_IMAGE = "custom-marker";
    private static final String mapboxKey = "pk.eyJ1Ijoic3RyaXBlZHdyaXN0YmFuZHMiLCJhIjoiY2pvN3VrYWx6MDJsZjN3dGt1bDNjd2c0aiJ9.qeI4-uMxyL5JnEiPi3UVSQ";
    private double currentLattitude = 0;
    private double currentLongitute = 0;

    private static final String GEOJSON_SOURCE_ID = "GEOJSONFILE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, mapboxKey);

        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
            mapboxMap.setStyle(new Style.Builder().fromUrl("mapbox://styles/stripedwristbands/cjrs8ad75iwpe2so1sq19ayom"), new Style.OnStyleLoaded() {

                @Override
            public void onStyleLoaded(@NonNull Style style) {
                createGeoJsonSource(style);
                addPolygonLayer(style);
                addPointsLayer(style);
                    style.addImage(MARKER_IMAGE, BitmapFactory.decodeResource(
                            MainActivity.this.getResources(), R.drawable.custom_marker));
                    addMarkers(style);
            }
        });



        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(@NonNull LatLng point) {

                String string = String.format(Locale.US, "User clicked at: %s", point.toString());

                Toast.makeText(MainActivity.this, string, Toast.LENGTH_LONG).show();

                currentLattitude = point.getLatitude();
                currentLongitute = point.getLongitude();
                return false;
            }
        });



    }

    private void createGeoJsonSource(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addSource(new GeoJsonSource(GEOJSON_SOURCE_ID,
                loadJsonFromAsset("Tornado_Watch.geojson")));
    }

    private void addPolygonLayer(@NonNull Style loadedMapStyle) {
        FillLayer countryPolygonFillLayer = new FillLayer("polygon", GEOJSON_SOURCE_ID);
        countryPolygonFillLayer.setProperties(
                PropertyFactory.fillColor(Color.RED),
                PropertyFactory.fillOpacity(.4f));
        countryPolygonFillLayer.setFilter(eq(literal("$type"), literal("Polygon")));
        loadedMapStyle.addLayer(countryPolygonFillLayer);
    }

    private void addPointsLayer(@NonNull Style loadedMapStyle) {
        CircleLayer individualCirclesLayer = new CircleLayer("points", GEOJSON_SOURCE_ID);
        individualCirclesLayer.setProperties(
                PropertyFactory.circleColor(Color.YELLOW),
                PropertyFactory.circleRadius(3f));
        individualCirclesLayer.setFilter(eq(literal("$type"), literal("Point")));
        loadedMapStyle.addLayer(individualCirclesLayer);
    }

    private String loadJsonFromAsset(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void addMarkers(@NonNull Style loadedMapStyle) {
        List<Feature> features = new ArrayList<>();
        features.add(Feature.fromGeometry(Point.fromLngLat(-78.2794, 39.2386)));

        loadedMapStyle.addSource(new GeoJsonSource(MARKER_SOURCE, FeatureCollection.fromFeatures(features)));


        loadedMapStyle.addLayer(new SymbolLayer(MARKER_STYLE_LAYER, MARKER_SOURCE)
                .withProperties(
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconImage(MARKER_IMAGE),

                        PropertyFactory.iconOffset(new Float[] {0f, -52f})
                ));
    }



    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }


    public void switchSelect(View view) {

    }
}
