package edu.bsu.rdgunderson.armchairstormchaserapp;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private double currentLattitude = 40.193378;
    private double currentLongitute = -85.386360;
    private double destinationLattitude;
    private double destinationLongitute;

    private static final String GEOJSON_SOURCE_ID = "GEOJSONFILE";
//    private static final String GEOJSON_SOURCE_ID = "geojson-source";

    private static final String ROUTE_LAYER_ID = "route-layer-id";
    private static final String ROUTE_SOURCE_ID = "route-source-id";
    private static final String ICON_LAYER_ID = "icon-layer-id";
    private static final String ICON_SOURCE_ID = "icon-source-id";
    private static final String RED_PIN_ICON_ID = "red-pin-icon-id";
    private DirectionsRoute currentRoute;
    private MapboxDirections client;
    private Point origin;
    private Point destination;

    private boolean isMarkers = false;
//    private boolean isWeather = false;
    private boolean isDestinationMarkers = false;
    private SymbolLayer originMarkerSymbolLayer = null;
    private SymbolLayer symbolLayer = null;
    private GeoJsonSource originMarkerGeoJsonSource = null;
    private SymbolLayer symbolLayer2 = null;
    private GeoJsonSource destinationMarkergeoJsonSource = null;
    final Handler handler = new Handler();
    Timer timer;
    TimerTask timerTask;
    private boolean hasSetRoute = false;

    private View login;

    public Point currentLocation = Point.fromLngLat(currentLongitute, currentLattitude);

    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, Constants.MAPBOX_API_KEY);

        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        mapboxMap.setStyle(new Style.Builder().fromUrl(Constants.MAPBOX_STYLE_URL), new Style.OnStyleLoaded() {
        //mapboxMap.setStyle(Style.LIGHT, new Style.OnStyleLoaded() {
                @Override
            public void onStyleLoaded(@NonNull Style style) {
                createGeoJsonSource(style);
                addPolygonLayer(style);

                    ArmchairStormChaser app = (ArmchairStormChaser)getApplication();
                    socket = app.getSocket();

                    // socket.on(Socket.EVENT_CONNECT, onConnect);
                    // socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
                    // socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
                    // socket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
                    socket.on("updatePlayer", onUpdatePlayer);
                    socket.connect();

                //setContentView(R.layout.mapbox_view_search);

                    //Add Current Position Icon on App start
                    style.addImage("origin-marker-icon-id",
                            BitmapFactory.decodeResource(
                                    MainActivity.this.getResources(), R.drawable.custom_marker));

                    //Retrieve current location from server
                    originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(
                            Point.fromLngLat(currentLongitute, currentLattitude)));
                    style.addSource(originMarkerGeoJsonSource);

                    originMarkerSymbolLayer = new SymbolLayer("originMarker-layer-id", "origin-source-id");
                    originMarkerSymbolLayer.withProperties(
                            PropertyFactory.iconImage("origin-marker-icon-id")
                    );
                    style.addLayer(originMarkerSymbolLayer.withProperties(
                            iconAllowOverlap(true),
                            iconIgnorePlacement(true)
                    ));

                    startTimer();
                }

            public void startTimer() {
                //set a new Timer
                timer = new Timer();

                //initialize the TimerTask's job
                initializeTimerTask();

                //schedule the timer, after the first 5000ms the TimerTask will run every 10000ms
                timer.schedule(timerTask, 5000, 5000); //
            }

            public void stoptimertask(View v) {
                //stop the timer, if it's not already null
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
            }

            public void initializeTimerTask() {

                timerTask = new TimerTask() {
                    public void run() {

                        //use a handler to run a toast that shows the current timestamp
                        handler.post(new Runnable() {
                            public void run() {
                                //Update player when timer task runs
                                if (hasSetRoute) {
                                    socket.emit("getPlayerUpdate");
                                }
                                Style style = mapboxMap.getStyle();

                                //Remove Markers
                                style.removeLayer("originMarker-layer-id");
                                style.removeSource(originMarkerGeoJsonSource);
                                /*if (style.getSource("origin-source-id") != null) {
                                    style.removeLayer("originaMarker-layer-id");
                                    style.removeSource(destinationMarkergeoJsonSource);
                                }*/
                                //Add Current Position Icon
                                style.addImage("origin-marker-icon-id",
                                        BitmapFactory.decodeResource(
                                                MainActivity.this.getResources(), R.drawable.custom_marker));

                                /*originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(
                                        Point.fromLngLat(currentLongitute, currentLattitude)));*/
                                originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(currentLocation));
                                style.addSource(originMarkerGeoJsonSource);

                                symbolLayer = new SymbolLayer("originMarker-layer-id", "origin-source-id");
                                symbolLayer.withProperties(
                                        PropertyFactory.iconImage("origin-marker-icon-id")
                                );
                                style.addLayer(symbolLayer.withProperties(
                                        iconAllowOverlap(true),
                                        iconIgnorePlacement(true),
                                        iconOpacity((float) 1.00)
                                ));

                                //If there is a destination from the server, get destination and set route on screen to route between current destination and current location

                                //Add Destination Icon
                                /*style.addImage("destination-marker-icon-id",
                                        BitmapFactory.decodeResource(
                                                MainActivity.this.getResources(), R.drawable.custom_marker));

                                destinationMarkergeoJsonSource = new GeoJsonSource("destination-source-id", Feature.fromGeometry(
                                        Point.fromLngLat(destinationLongitute, destinationLattitude)));
                                style.addSource(destinationMarkergeoJsonSource);

                                symbolLayer2 = new SymbolLayer("destinationMarker-layer-id", "destination-source-id");
                                symbolLayer2.withProperties(
                                        PropertyFactory.iconImage("destination-marker-icon-id")
                                );
                                style.addLayer(symbolLayer2);*/

//                                isMarkers = true;

                            }
                        });
                    }
                };
            }


        });

        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(@NonNull LatLng point) {

                hasSetRoute = true;

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
                if (isMarkers && isDestinationMarkers) {
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
                isDestinationMarkers = true;

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
                //response.body().waypoints();
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
//        loadedMapStyle.addSource(new GeoJsonSource(loadJsonFromAsset("Tornado_Watch.geojson")));
        loadedMapStyle.addSource(new GeoJsonSource(GEOJSON_SOURCE_ID,
                loadJsonFromAsset("Tornado_Watch.geojson")));
        /*CustomGeometrySource source = new CustomGeometrySource("geojson-source", (FeatureCollection.fromJson(loadJsonFromAsset("Tornado_Watch.geojson"))));
        loadedMapStyle.addSource(source);*/
    }

    private void addPolygonLayer(@NonNull Style loadedMapStyle) {
        FillLayer countryPolygonFillLayer = new FillLayer("polygon", GEOJSON_SOURCE_ID);
        countryPolygonFillLayer.setProperties(
                PropertyFactory.fillOpacity(.4f));
        countryPolygonFillLayer.setFilter(eq(literal("$type"), literal("Polygon")));
        loadedMapStyle.addLayer(countryPolygonFillLayer);
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


    /*
        Event fires when server emits the updatePlayer event
        server emits in response to an emit from App side of
        socket.emit("getPlayerUpdate");
     */
    private Emitter.Listener onUpdatePlayer = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        Point currentLocation;
                        int score;
                        JSONArray latlong;
                        try{
                            latlong = data.getJSONArray("currentLocation");
                            currentLocation = Point.fromLngLat(latlong.getDouble(0), latlong.getDouble(1));
                            score = data.getInt("currentScore");

                            // call required method for updating icon location and pass it currentLocation
                            updateLocation(currentLocation);
                            // update score display once it is implemented
                            updateScore(score);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
        }

    };

    private void updateScore(int score) {
        Toast.makeText(getApplicationContext(),"Score: " + score, Toast.LENGTH_SHORT).show();
    }

    public void updateLocation(Point currentLocationFromServer) {
        currentLongitute = currentLocationFromServer.longitude();
        currentLattitude = currentLocationFromServer.latitude();
        currentLocation = Point.fromLngLat(currentLongitute, currentLattitude);
        System.out.println(currentLocation);
    }

    public void switchToLoginScreen(View view) {
        LayoutInflater inflater = getLayoutInflater();
        login = inflater.inflate(R.layout.login, null);
        getWindow().addContentView(login, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

    }

    public void switchToMainScreen(View view) {
        ((ViewGroup) login.getParent()).removeView(login);
    }

}
