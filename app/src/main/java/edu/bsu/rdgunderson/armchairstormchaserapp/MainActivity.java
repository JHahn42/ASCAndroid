package edu.bsu.rdgunderson.armchairstormchaserapp;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.mapbox.mapboxsdk.Mapbox;
//import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.picker.PlacePicker;
import com.mapbox.mapboxsdk.plugins.places.picker.model.PlacePickerOptions;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.maps.Style;
import android.os.Bundle;
import android.support.annotation.Nullable;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
//import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;
import android.graphics.BitmapFactory;
import edu.bsu.rdgunderson.armchairstormchaserapp.R;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private static final int PLACE_SELECTION_REQUEST_CODE = 56789;
    private static final String MARKER_SOURCE = "markers-source";
    private static final String MARKER_STYLE_LAYER = "markers-style-layer";
    private static final String MARKER_IMAGE = "custom-marker";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String mapboxKey = "pk.eyJ1Ijoic3RyaXBlZHdyaXN0YmFuZHMiLCJhIjoiY2pvN3VrYWx6MDJsZjN3dGt1bDNjd2c0aiJ9.qeI4-uMxyL5JnEiPi3UVSQ";
        Mapbox.getInstance(this, mapboxKey);
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
//        mapView.setStyleUrl("mapbox://styles/stripedwristbands/cjrs8ad75iwpe2so1sq19ayom");


        mapView.getMapAsync(new OnMapReadyCallback() {

            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
//                mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                mapboxMap.setStyle(new Style.Builder().fromUrl("mapbox://styles/stripedwristbands/cjrs8ad75iwpe2so1sq19ayom"), new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {

                        // Map is set up and the style has loaded. Now you can add data or make other map adjustments


                    }
                });
            }
        });
    }

    //Mapbox code to create a marker
    /*@Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, "pk.eyJ1Ijoic3RyaXBlZHdyaXN0YmFuZHMiLCJhIjoiY2pvN3VrYWx6MDJsZjN3dGt1bDNjd2c0aiJ9.qeI4-uMxyL5JnEiPi3UVSQ");

        setContentView(R.layout.activity_main);

        *//* Map: This represents the map in the application. *//*
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        mapboxMap.setStyle(new Style.Builder().fromUrl(mapbox://styles/mapbox/light-v10), new Style.OnStyleLoaded() {
        @Override
        public void onStyleLoaded(@NonNull Style style) {
            *//* Image: An image is loaded and added to the map. *//*
            style.addImage(MARKER_IMAGE, BitmapFactory.decodeResource(
                    MainActivity.this.getResources(), R.drawable.custom_marker));
            addMarkers(style);
        }
    });
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
    }*/



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
}
