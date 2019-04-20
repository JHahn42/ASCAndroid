package edu.bsu.rdgunderson.armchairstormchaserapp;

import timber.log.Timber;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import static java.lang.Math.floor;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private MapboxMap map;
    private double currentLattitude = 40.193378;
    private double currentLongitute = -85.386360;
    private double destinationLattitude;
    private double destinationLongitute;

    private static final String GEOJSON_SOURCE_ID = "GEOJSONFILE";

    private static final String ROUTE_LAYER_ID = "route-layer-id";
    private static final String ROUTE_SOURCE_ID = "route-source-id";
    private static final String ICON_LAYER_ID = "icon-layer-id";
    private static final String ICON_SOURCE_ID = "icon-source-id";
    private static final String RED_PIN_ICON_ID = "red-pin-icon-id";
    private DirectionsRoute currentRoute;
    private MapboxDirections client;
    private Point origin;
    private Point destination;
    private SymbolLayer originMarkerSymbolLayer = null;
    private GeoJsonSource originMarkerGeoJsonSource = null;
    final Handler handler = new Handler();
    Timer timer;
    TimerTask timerTask;
    private View login;

    private boolean isMarkers = false;
//    private boolean isWeather = false;
    private boolean isDestinationMarkers = false;
    private boolean hasSetRoute = false;
    private boolean loggedIn = false;
    private boolean inFocus = true;

    public Point currentLocation = Point.fromLngLat(currentLongitute, currentLattitude);

    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, Constants.MAPBOX_API_KEY);
        /*while (!loggedIn) {
            setContentView(R.layout.login);
            //When button is clicked on login screen, check if player entered exists
            //If player exists setLoggedIn = true
        }
        */
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        mapboxMap.setStyle(new Style.Builder().fromUrl(Constants.MAPBOX_STYLE_URL), new Style.OnStyleLoaded() {
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
                socket.on("destinationReached", destinationReached);
                socket.connect();

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
                map = mapboxMap;
                startTimer();
            }

            public void startTimer() {
                timer = new Timer();
                initializeTimerTask();
                timer.schedule(timerTask, 5000, Constants.REFRESH_RATE_IN_SECONDS * 1000);
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
                        handler.post(new Runnable() {
                            public void run() {
                                //Update player when timer task runs if the player has selected a route
                                if (inFocus) {
                                    socket.emit("getPlayerUpdate");
                                    updateMarkerPosition();
                                }
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
                updateMarkerPosition();
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
                System.out.println(call.request().url().toString());

                Timber.d("Response code: " + response.code());
                if (response.body() == null) {
                    Timber.e("No routes found, make sure you set the right user and access token.");
                    return;
                } else if (response.body().routes().size() < 1) {
                    Timber.e("No routes found");
                    return;
                }

                //Send Current Route GeoJson file to server
                currentRoute = response.body().routes().get(0);
                socket.emit("setTravelRoute", currentRoute.geometry(), currentRoute.distance(), currentRoute.duration());

                addRouteToStyle(style);

            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("Error: " + throwable.getMessage());
            }
        });
    }

    private void addRouteToStyle(Style style) {
        if (style.isFullyLoaded()) {
            GeoJsonSource source = style.getSourceAs(ROUTE_SOURCE_ID);

            if (source != null) {
                Timber.d("onResponse: source != null");
                source.setGeoJson(FeatureCollection.fromFeature(
                        Feature.fromGeometry(LineString.fromPolyline(currentRoute.geometry(), PRECISION_6))));
            }
        }
    }

    private void removeRoute() {
        Style style = map.getStyle();
        style.removeLayer(ROUTE_LAYER_ID);
    }

    private void createGeoJsonSource(@NonNull Style loadedMapStyle) {
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
                        double timeLeft;
                        JSONArray latlong;
                        try{
                            latlong = data.getJSONArray("currentLocation");
                            currentLocation = Point.fromLngLat(latlong.getDouble(0), latlong.getDouble(1));
                            score = data.getInt("currentScore");
                            timeLeft = data.getDouble("timeLeft");
                            updateLocation(currentLocation);
                            updateTimeLeft(timeLeft);
                            updateScore(score);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
        }
    };

    private Emitter.Listener destinationReached = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (inFocus) {
                        removeRoute();
                        hasSetRoute = false;
                    } else {
                        NotificationManager notif=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                        Notification notify=new Notification.Builder
                                (getApplicationContext()).setContentTitle("Test").setContentText("Congratulations").
                                setContentTitle("You have reached your destination!").setSmallIcon(R.drawable.custom_marker).build();
                        notify.flags |= Notification.FLAG_AUTO_CANCEL;
                        notif.notify(0, notify);
                    }
                }
            });
        }
    };

    private void updateTimeLeft(double timeLeft) {
        TextView timeText = findViewById(R.id.textView_Time);
        timeText.setText(Integer.toString((int) floor(timeLeft)) + " Seconds");
    }

    private void updateScore(int score) {
        TextView scoreText = findViewById(R.id.textView_Score);
        scoreText.setText(Integer.toString(score));
    }

    public void updateLocation(Point currentLocationFromServer) {
        currentLongitute = currentLocationFromServer.longitude();
        currentLattitude = currentLocationFromServer.latitude();
        currentLocation = Point.fromLngLat(currentLongitute, currentLattitude);
    }

    private void updateMarkerPosition() {
        if (map.getStyle() != null) {
            originMarkerGeoJsonSource = map.getStyle().getSourceAs("origin-source-id");
            if (originMarkerGeoJsonSource != null) {
                originMarkerGeoJsonSource.setGeoJson(FeatureCollection.fromFeature(
                        Feature.fromGeometry(Point.fromLngLat(currentLocation.longitude(), currentLocation.latitude()))
                ));
            }
        }
    }

    public void stopTravel(View view) {
        socket.emit("stopTravel");
        removeRoute();
        updateTimeLeft(0);
        hasSetRoute = false;
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

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        inFocus = true;
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        inFocus = false;
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
