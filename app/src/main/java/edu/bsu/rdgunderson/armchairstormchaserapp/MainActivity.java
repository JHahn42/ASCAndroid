package edu.bsu.rdgunderson.armchairstormchaserapp;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.Socket;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.rasterOpacity;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private static final int PLACE_SELECTION_REQUEST_CODE = 56789;
    private static final String MARKER_SOURCE = "markers-source";
    private static final String MARKER_STYLE_LAYER = "markers-style-layer";
    private static final String MARKER_IMAGE = "custom-marker";
    private double currentLattitude = 40.193378;
    private double currentLongitute = -85.386360;
    private double destinationLattitude = 41.878113;
    private double destinationLongitute = -87.629799;

    private static final String GEOJSON_SOURCE_ID = "GEOJSONFILE";

    private static final String ROUTE_LAYER_ID = "route-layer-id";
    private static final String ROUTE_SOURCE_ID = "route-source-id";
    private static final String ICON_LAYER_ID = "icon-layer-id";
    private static final String ICON_SOURCE_ID = "icon-source-id";
    private static final String RED_PIN_ICON_ID = "red-pin-icon-id";
    private MapboxMap mapboxMap;
    private DirectionsRoute currentRoute;
    private MapboxDirections client;
    private Point origin;
    private Point destination;

    private boolean isMarkers = false;
    private SymbolLayer originMarkerSymbolLayer = null;
    private SymbolLayer symbolLayer = null;
    private GeoJsonSource originMarkerGeoJsonSource = null;
    private SymbolLayer symbolLayer2 = null;
    private GeoJsonSource destinationMarkergeoJsonSource = null;


    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArmchairStormChaser app = (ArmchairStormChaser)getApplication();
        socket = app.getSocket();
        socket.connect();


        Mapbox.getInstance(this, Constants.MAPBOX_API_KEY);

        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        Intent update = new Intent(this, updateLocation.class);
        this.startService(update);
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        mapboxMap.setStyle(new Style.Builder().fromUrl(Constants.MAPBOX_STYLE_URL), new Style.OnStyleLoaded() {
                @Override
            public void onStyleLoaded(@NonNull Style style) {
                createGeoJsonSource(style);
                addPolygonLayer(style);
                addPointsLayer(style);

                    //Add Current Position Icon on App start
                    style.addImage("origin-marker-icon-id",
                            BitmapFactory.decodeResource(
                                    MainActivity.this.getResources(), R.drawable.custom_marker));

                    originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(
                            Point.fromLngLat(currentLongitute, currentLattitude)));
                    style.addSource(originMarkerGeoJsonSource);

                    originMarkerSymbolLayer = new SymbolLayer("originMarker-layer-id", "origin-source-id");
                    originMarkerSymbolLayer.withProperties(
                            PropertyFactory.iconImage("origin-marker-icon-id")
                    );
                    style.addLayer(originMarkerSymbolLayer);

                /*style.addImage(MARKER_IMAGE, BitmapFactory.decodeResource(
                            MainActivity.this.getResources(), R.drawable.custom_marker));
                    addMarkers(style);*/
                    // Create a data source for the satellite raster image and add the source to the map

                    //style.addSource(new RasterSource("SATELLITE_RASTER_SOURCE_ID",
                            //"mapbox://styles/stripedwristbands/cjojc7pow0bjv2st5rkoinjza", 512));

                    //mapbox://styles/stripedwristbands/cjojc7pow0bjv2st5rkoinjza
                    // Create a new map layer for the satellite raster images and add the satellite layer to the map.
                    // Use runtime styling to adjust the satellite layer's opacity based on the map camera's zoom level

                    /*style.addLayer(
                            new RasterLayer("SATELLITE_RASTER_LAYER_ID", "SATELLITE_RASTER_SOURCE_ID").withProperties(
                                    rasterOpacity(interpolate(linear(), zoom(),
                                            stop(6, 0),
                                            stop(7, 1)
                                    ))));*/

                }

        });

        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(@NonNull LatLng point) {
                destinationLattitude = point.getLatitude();
                destinationLongitute = point.getLongitude();

                //Retrieve "origin" (current location) from server
                origin = Point.fromLngLat(currentLongitute, currentLattitude);

                //Send Destination Information to server
                //When destination change is sent server should automatically change course
                destination = Point.fromLngLat(destinationLongitute, destinationLattitude);

                Style style = mapboxMap.getStyle();
                initSource(style);

                initLayers(style);

                getRoute(style, origin, destination);

                style.removeLayer("originMarker-layer-id");
                style.removeSource(originMarkerGeoJsonSource);
                if (isMarkers) {
                    //Remove Markers
                    /*style.removeLayer("originMarker-layer-id");
                    style.removeSource(originMarkerGeoJsonSource);*/
                    style.removeLayer("destinationMarker-layer-id");
                    style.removeSource(destinationMarkergeoJsonSource);
                }
                //Add Current Position Icon
                style.addImage("origin-marker-icon-id",
                        BitmapFactory.decodeResource(
                                MainActivity.this.getResources(), R.drawable.custom_marker));

                originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(
                        Point.fromLngLat(currentLongitute, currentLattitude)));
                style.addSource(originMarkerGeoJsonSource);

                symbolLayer = new SymbolLayer("originMarker-layer-id", "origin-source-id");
                symbolLayer.withProperties(
                        PropertyFactory.iconImage("origin-marker-icon-id")
                );
                style.addLayer(symbolLayer);

                //Add Destination Icon
                style.addImage("destination-marker-icon-id",
                        BitmapFactory.decodeResource(
                                MainActivity.this.getResources(), R.drawable.custom_marker));

                destinationMarkergeoJsonSource = new GeoJsonSource("destination-source-id", Feature.fromGeometry(
                        Point.fromLngLat(destinationLongitute, destinationLattitude)));
                style.addSource(destinationMarkergeoJsonSource);

                symbolLayer2 = new SymbolLayer("destinationMarker-layer-id", "destination-source-id");
                symbolLayer2.withProperties(
                        PropertyFactory.iconImage("destination-marker-icon-id")
                );
                style.addLayer(symbolLayer2);

                isMarkers = true;

                return false;
            }
        });
    }

    private void initSource(@NonNull Style loadedMapStyle) {

        if (loadedMapStyle.getSourceAs(ROUTE_SOURCE_ID) == null) {
            loadedMapStyle.addSource(new GeoJsonSource(ROUTE_SOURCE_ID,
                    FeatureCollection.fromFeatures(new Feature[]{})));
        }
        GeoJsonSource iconGeoJsonSource = new GeoJsonSource(ICON_SOURCE_ID, FeatureCollection.fromFeatures(new Feature[] {
                Feature.fromGeometry(Point.fromLngLat(origin.longitude(), origin.latitude())),
                Feature.fromGeometry(Point.fromLngLat(destination.longitude(), destination.latitude()))}));
        if (loadedMapStyle.getSourceAs(ICON_SOURCE_ID) == null) {
            loadedMapStyle.addSource(iconGeoJsonSource);
        }
    }

    private void initLayers(@NonNull Style loadedMapStyle) {
        LineLayer routeLayer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID);

        routeLayer.setProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(5f),
                lineColor(Color.parseColor("#009688"))
        );

        if (loadedMapStyle.getLayer(ROUTE_LAYER_ID) == null) {
            loadedMapStyle.addLayer(routeLayer);
        }
        /*loadedMapStyle.addImage(RED_PIN_ICON_ID, BitmapUtils.getBitmapFromDrawable(
                getResources().getDrawable(R.drawable.custom_marker)));*/

        if (loadedMapStyle.getLayer(ICON_LAYER_ID) == null) {
            loadedMapStyle.addLayer(new SymbolLayer(ICON_LAYER_ID, ICON_SOURCE_ID).withProperties(
                    iconImage(RED_PIN_ICON_ID),
                    iconIgnorePlacement(true),
                    iconIgnorePlacement(true),
                    iconOffset(new Float[]{0f, -4f})));
        }
    }

    private void getRoute(@NonNull final Style style, Point origin, Point destination) {

        client = MapboxDirections.builder()
                .origin(origin)
                .destination(destination)
                .overview(DirectionsCriteria.OVERVIEW_SIMPLIFIED)
                .profile(DirectionsCriteria.PROFILE_DRIVING)
                .geometries(DirectionsCriteria.GEOMETRY_POLYLINE6)
                .accessToken(Constants.MAPBOX_API_KEY)
                .build();

        client.enqueueCall(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
//                mapboxMap.setStyle(new Style.Builder().fromUrl("mapbox://styles/stripedwristbands/cjrs8ad75iwpe2so1sq19ayom"));
//                Style style = mapboxMap.getStyle();
                System.out.println(call.request().url().toString());

                Timber.d("Response code: " + response.code());
                if (response.body() == null) {
                    Timber.e("No routes found, make sure you set the right user and access token.");
                    return;
                } else if (response.body().routes().size() < 1) {
                    Timber.e("No routes found");
                    return;
                }

                //Send Current ROute GeoJson file to server
                currentRoute = response.body().routes().get(0);
                socket.emit("setTravelRoute", currentRoute.geometry(), currentRoute.distance(), currentRoute.duration());

                if (style.isFullyLoaded()) {
                    GeoJsonSource source = style.getSourceAs(ROUTE_SOURCE_ID);

                    if (source != null) {
                        Timber.d("onResponse: source != null");
                        source.setGeoJson(FeatureCollection.fromFeature(
                                Feature.fromGeometry(LineString.fromPolyline(currentRoute.geometry(), PRECISION_6))));
                    }
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("Error: " + throwable.getMessage());
            }
        });
    }

    private void createGeoJsonSource(@NonNull Style loadedMapStyle) {
        //Instead of loading from assets folder, retrieve from server
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
                        iconIgnorePlacement(true),
                        iconImage(MARKER_IMAGE),

                        iconOffset(new Float[] {0f, -52f})
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

}
