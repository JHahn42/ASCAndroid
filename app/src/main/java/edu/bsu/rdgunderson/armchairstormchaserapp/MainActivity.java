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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillOutlineColor;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.MultiPolygon;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.utils.PolylineUtils;
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
import com.mapbox.mapboxsdk.style.sources.VectorSource;

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

    private MapboxMap map;
    /*private double currentLatitude = 40.193378;
    private double currentLongitude = -85.386360;*/
    private MapView mapView;
    private double currentLatitude;
    private double currentLongitude;
    private double destinationLatitude;
    private double destinationLongitude;
    private static final String GEOJSON_SOURCE_ID = "GEOJSONFILE";
    private static final String ROUTE_LAYER_ID = "route-layer-id";
    private static final String ROUTE_SOURCE_ID = "route-source-id";
    private static final String ICON_LAYER_ID = "icon-layer-id";
    private static final String ICON_SOURCE_ID = "icon-source-id";
    private static final String RED_PIN_ICON_ID = "red-pin-icon-id";
    private DirectionsRoute currentRoute;
    private Point origin;
    private Point destination;
    private SymbolLayer originMarkerSymbolLayer = null;
    private GeoJsonSource originMarkerGeoJsonSource = null;
    final Handler handler = new Handler();
    Timer timer;
    TimerTask timerTask;
    private View loginScreen;
    private View inputConfirmationScreen;
    private View endOfDayScreen;

//    private boolean isMarkers = false;
//    private boolean isWeather = false;
//    private boolean isDestinationMarkers = false;
    private boolean hasSetRoute = false;
    private boolean loggedIn = false;
    private boolean inFocus = true;
    private boolean endTravelEnabledDisable = false;
    public boolean isEndOfDay = false;
    public boolean isSelectingStartingLocation = true;

    public Point currentLocation = Point.fromLngLat(currentLongitude, currentLatitude);

    private Socket socket;

    private MultiPolygon tornadoWarnings;
    private MultiPolygon tornadoWatches;
    private MultiPolygon tsWarnings;
    private MultiPolygon tsWatches;
    private List<Feature> windPoints = new ArrayList<>();
    private List<Feature> tornadoPoints = new ArrayList<>();
    private List<Feature> hailSmall = new ArrayList<>();
    private List<Feature> hailOneInch = new ArrayList<>();
    private List<Feature> hailTwoInch = new ArrayList<>();
    private List<Feature> hailThreeInch = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArmchairStormChaser app = (ArmchairStormChaser)getApplication();

        socket = app.getSocket();
        // socket.on(Socket.EVENT_CONNECT, onConnect);
        // socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        // socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        // socket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        socket.on("updatePlayer", onUpdatePlayer);
        socket.on("destinationReached", destinationReached);
        socket.on("endOfDay", endOfDay);
        socket.on("weatherUpdate", weatherUpdate);
        socket.connect();

        Mapbox.getInstance(this, Constants.MAPBOX_API_KEY);
        /*while (!loggedIn) {
            setContentView(R.layout.loginScreen);
            //When button is clicked on loginScreen screen, check if player entered exists
            //If player exists setLoggedIn = true
        }*/

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

                map = mapboxMap;

                socket.emit("getWeatherUpdate");

                startTimer();
            }

            void startTimer() {
                timer = new Timer();
                initializeTimerTask();
                timer.schedule(timerTask, 0, Constants.REFRESH_RATE_IN_SECONDS * 1000);
            }

            void initializeTimerTask() {

                timerTask = new TimerTask() {
                    public void run() {
                        handler.post(new Runnable() {
                            public void run() {
                                //Update player when timer task runs if the player has selected a route and is not selecting a starting location
                                if (inFocus && !isSelectingStartingLocation) {
                                    socket.emit("getPlayerUpdate");
                                    updateMarkerPosition();
                                }
                                endTravelEnabledDisable = hasSetRoute;
                                toggleStopTravelButton(endTravelEnabledDisable);
                            }
                        });
                    }
                };
            }
        });

        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(@NonNull LatLng point) {
                if (!endTravelEnabledDisable) {
                    if (!isSelectingStartingLocation) {
                        hasSetRoute = true;
                        destinationLatitude = point.getLatitude();
                        destinationLongitude = point.getLongitude();
                        origin = Point.fromLngLat(currentLongitude, currentLatitude);
                        destination = Point.fromLngLat(destinationLongitude, destinationLatitude);
                        Style style = mapboxMap.getStyle();
                        initSource(style);
                        initLayers(style);
                        getRoute(style, origin, destination);
                        updateMarkerPosition();
                    } else {
                        currentLatitude = point.getLatitude();
                        currentLongitude = point.getLongitude();
                        placeStartingLocationMarker();
                        changeStartingLocationText();
//                        socket.emit("selectStartingLocation", currentLatitude, currentLongitude);
                        isSelectingStartingLocation = false;
                    }
                }
                return false;
            }
        });
    }

    private void placeStartingLocationMarker() {
        Style style = map.getStyle();
        style.addImage("origin-marker-icon-id",
                BitmapFactory.decodeResource(
                        MainActivity.this.getResources(), R.drawable.asc_logo_small));
        originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(
                Point.fromLngLat(currentLongitude, currentLatitude)));
        style.addSource(originMarkerGeoJsonSource);

        originMarkerSymbolLayer = new SymbolLayer("originMarker-layer-id", "origin-source-id");
        originMarkerSymbolLayer.withProperties(
                PropertyFactory.iconImage("origin-marker-icon-id")
        );
        style.addLayer(originMarkerSymbolLayer.withProperties(
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        ));
    }

    private void changeStartingLocationText() {
        TextView selectRoute = findViewById(R.id.textView_selectNewRoute);
        selectRoute.setText(getApplicationContext().getResources().getString(R.string.selectNewRouteLabel));
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

        MapboxDirections client = MapboxDirections.builder()
                .origin(origin)
                .destination(destination)
                .overview(DirectionsCriteria.OVERVIEW_SIMPLIFIED)
                .profile(DirectionsCriteria.PROFILE_DRIVING)
                .geometries(DirectionsCriteria.GEOMETRY_POLYLINE6)
                .accessToken(Constants.MAPBOX_API_KEY)
                .build();

        client.enqueueCall(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(@NonNull Call<DirectionsResponse> call, @NonNull Response<DirectionsResponse> response) {
//                System.out.println(call.request().url().toString());
//                Timber.d("Response code: %s", response.code());
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
            public void onFailure(@NonNull Call<DirectionsResponse> call, @NonNull Throwable throwable) {
//                Timber.e("Error: %s", throwable.getMessage());
            }
        });
    }

    private void addRouteToStyle(Style style) {
        if (style.isFullyLoaded()) {
            GeoJsonSource source = style.getSourceAs(ROUTE_SOURCE_ID);
            if (source != null) {
                Timber.d("onResponse: source != null");
                source.setGeoJson(FeatureCollection.fromFeature(
                        Feature.fromGeometry(LineString.fromPolyline(Objects.requireNonNull(currentRoute.geometry()), PRECISION_6))));
            }
        }
    }

    private void removeRoute() {
        Style style = map.getStyle();
        assert style != null;
        style.removeLayer(ROUTE_LAYER_ID);
    }

    private void addPolygonLayer(@NonNull Style loadedMapStyle, String layerId, String sourceId, MultiPolygon polygons, String color) {
        GeoJsonSource source = loadedMapStyle.getSourceAs(sourceId);
        if(source != null) {
            loadedMapStyle.removeLayer(layerId);
            loadedMapStyle.removeSource(sourceId);
        }

        loadedMapStyle.addSource(new GeoJsonSource(sourceId, polygons));
        loadedMapStyle.addLayer(new FillLayer(layerId, sourceId).withProperties(
                PropertyFactory.fillColor(Color.parseColor(color)),PropertyFactory.fillOpacity(.6f),PropertyFactory.fillOutlineColor(Color.parseColor("#000000"))
        ));

    }

    private void addPointLayer(@NonNull Style loadedMapStyle, String layerId, String sourceId, List<Feature> points, String color) {
        GeoJsonSource source = loadedMapStyle.getSourceAs(sourceId);
        if(source != null) {
            loadedMapStyle.removeLayer(layerId);
            loadedMapStyle.removeSource(sourceId);
        }
        loadedMapStyle.addSource(new GeoJsonSource(sourceId, FeatureCollection.fromFeatures(points)));
        loadedMapStyle.addLayer(new CircleLayer(layerId, sourceId).withProperties(
                PropertyFactory.circleColor(Color.parseColor(color)),
                PropertyFactory.circleRadius(3f)
                ));

    }

    private String loadJsonFromAsset(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);

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

    private Emitter.Listener weatherUpdate = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject obj = (JSONObject) args[0];

                    try{
                        JSONArray weather = obj.getJSONArray("storms");

                        JSONArray tornWarn = weather.getJSONArray(0);
                        if(tornWarn.length() > 0) {
                            tornadoWarnings = fillStorm(tornWarn);
                            addPolygonLayer(map.getStyle(), "tornado_warnings_layer", "tornado_warnings_source", tornadoWarnings, "#cc0e0e");
                        }

                        JSONArray tornWatch = weather.getJSONArray(1);
                        if(tornWatch.length() > 0) {
                            tornadoWatches = fillStorm(tornWatch);
                            addPolygonLayer(map.getStyle(), "tornado_watch_layer", "tornado_watch_source", tornadoWatches, "#f79533");
                        }

                        JSONArray tsWarn = weather.getJSONArray(2);
                        if(tsWarn.length() > 0) {
                            tsWarnings = fillStorm(tsWarn);
                            addPolygonLayer(map.getStyle(), "thunderstorm_warning_layer", "thunderstorm_warning_source", tsWarnings, "#0c0f7a");
                        }

                        JSONArray tsWatch = weather.getJSONArray(3);
                        if(tsWatch.length() > 0) {
                            tsWatches = fillStorm(tsWatch);
                            addPolygonLayer(map.getStyle(), "thunderstorm_watch_layer", "thunderstorm_warning_source", tsWatches, "#48ddea");
                        }

                        JSONArray wind = weather.getJSONArray(4);
                        Log.d("WIND", wind.toString());
                        if(wind.length() > 0) {
                            windPoints = fillPointStorm(wind);
                            addPointLayer(map.getStyle(), "wind_layer", "wind_source", windPoints, "#a7c5c6");
                        }

                        JSONArray tornado = weather.getJSONArray(5);
                        if(tornado.length() > 0) {
                            tornadoPoints = fillPointStorm(tornado);
                            addPointLayer(map.getStyle(), "tornado_layer", "tornado_source", tornadoPoints, "#960606");
                        }

//                        JSONArray hail = weather.getJSONArray(6);
//                        if(hail.length() > 0) {
//                            fillHailStorm(hail);
//                            if(!hailSmall.isEmpty()) {
//                                addPointLayer(map.getStyle(), "", "", hailSmall);
//                            }
//                            if(!hailOneInch.isEmpty()) {
//                                addPointLayer(map.getStyle(), "", "", hailOneInch);
//                            }
//                            if(!hailTwoInch.isEmpty()) {
//                                addPointLayer(map.getStyle(), "", "", hailTwoInch);
//                            }
//                            if(!hailThreeInch.isEmpty()) {
//                                addPointLayer(map.getStyle(), "", "", hailThreeInch);
//                            }
//                        }


                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    };

    private MultiPolygon fillStorm(JSONArray storm) throws JSONException {
        ArrayList<Polygon> tempStorms = new ArrayList<Polygon>();

        for(int i = 0; i < storm.length(); i++) {

            List<List<Point>> points = new ArrayList<>();
            List<Point> outerPoints = new ArrayList<>();

            JSONArray coordinates = storm.getJSONObject(i).getJSONArray("coordinates").getJSONArray(0);
            for(int x = 0; x < coordinates.length(); x++){
                JSONArray longlat = coordinates.getJSONArray(x);
                outerPoints.add(Point.fromLngLat(longlat.getDouble(0), longlat.getDouble(1)));
            }
            points.add(outerPoints);
            tempStorms.add(Polygon.fromLngLats(points));

        }
        return MultiPolygon.fromPolygons(tempStorms);
    }

    private List<Feature> fillPointStorm(JSONArray storm) throws JSONException {
        List<Feature> tempFeat = new ArrayList<>();

        for(int i = 0; i < storm.length(); i++) {
            JSONArray coordinates = storm.getJSONObject(i).getJSONArray("coordinates");
            tempFeat.add(Feature.fromGeometry(Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))));
        }
        return tempFeat;
    }

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
                        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                        Notification notify=new Notification.Builder
                                (getApplicationContext()).setContentTitle("Test").setContentText("Congratulations").
                                setContentTitle("You have reached your destination!").setSmallIcon(R.drawable.asc_logo_small).build();
                        notify.flags |= Notification.FLAG_AUTO_CANCEL;
                        assert notificationManager != null;
                        notificationManager.notify(0, notify);
                    }
                }
            });
        }
    };

    //All End Of Day Screen methods
    private Emitter.Listener endOfDay = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int dailyScore = 0;
                    int totalScore = 0;
                    isEndOfDay = true;
                    setScoreOnEndOfDayScreen(dailyScore, totalScore);
                    switchToEndOfDayScreen();
                }
            });
        }
    };

    private void setScoreOnEndOfDayScreen(int dailyScore, int totalScore) {
        TextView dailyScoreText = findViewById(R.id.dailyScore_textView);
        TextView totalScoreText = findViewById(R.id.totalScore_textView);
        dailyScoreText.setText(Integer.toString(dailyScore));
        totalScoreText.setText(Integer.toString(totalScore));
    }

    public void beginNewDay(View view) {
        ((ViewGroup) endOfDayScreen.getParent()).removeView(endOfDayScreen);
    }

    public void switchToEndOfDayScreen() {
        LayoutInflater inflater = getLayoutInflater();
        endOfDayScreen = inflater.inflate(R.layout.end_of_day_screen, null);
        getWindow().addContentView(endOfDayScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /////////////////////////////////////////////////

    private void updateTimeLeft(double timeLeft) {
        TextView timeText = findViewById(R.id.textView_Time);
        timeText.setText(Integer.toString((int) floor(timeLeft)) + " Seconds");
    }

    private void updateScore(int score) {
        TextView scoreText = findViewById(R.id.textView_Score);
        scoreText.setText(Integer.toString(score));
    }

    public void updateLocation(Point currentLocationFromServer) {
        currentLongitude = currentLocationFromServer.longitude();
        currentLatitude = currentLocationFromServer.latitude();
        currentLocation = Point.fromLngLat(currentLongitude, currentLatitude);
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
        LayoutInflater inflater = getLayoutInflater();
        inputConfirmationScreen = inflater.inflate(R.layout.input_confirmation, null);
        getWindow().addContentView(inputConfirmationScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void toggleStopTravelButton(boolean enableDisable) {
        int toggleButton;
        int toggleText;
        Button stopTravel = findViewById(R.id.button_StopTravel);
        TextView selectRoute = findViewById(R.id.textView_selectNewRoute);
        if (enableDisable) {
            toggleButton = View.VISIBLE;
            toggleText = View.INVISIBLE;
        } else {
            toggleButton = View.INVISIBLE;
            toggleText = View.VISIBLE;
        }
        stopTravel.setVisibility(toggleButton);
        selectRoute.setVisibility(toggleText);
    }

    public void stopTravelButtonNo(View view){
        removeInputConfirmationScreen();
    }

    public void stopTravelButtonYes(View view){
        socket.emit("stopTravel");
        removeRoute();
        updateTimeLeft(0);
        hasSetRoute = false;
        removeInputConfirmationScreen();
    }

    private void removeInputConfirmationScreen(){
        ((ViewGroup) inputConfirmationScreen.getParent()).removeView(inputConfirmationScreen);
    }

    public void login(View view) {
        TextView usernameTextBox = findViewById(R.id.userName_text_input);
        TextView passwordTextBox = findViewById(R.id.password_text_input);
        if (usernameTextBox.getText() != null && passwordTextBox.getText() != null) {
            String usernameTextInput = usernameTextBox.getText().toString();
            String passwordTextInput = passwordTextBox.getText().toString();
            if (userExists(usernameTextInput, passwordTextInput)) {
//                socket.emit("login", usernameTextInput, Point.fromLngLat(currentLongitude, currentLatitude), 0, 0, 0);
                loggedIn = true;
            }
        }
    }

    public void logout(View view) {
//        socket.emit("logoff");
        switchToLoginScreen(view);
    }

    private boolean userExists(String usernameTextInput, String passwordTextInput) {
        //Replace with checking "Database/Data"
        return true;
    }

    public void switchToLoginScreen(View view) {
        LayoutInflater inflater = getLayoutInflater();
        loginScreen = inflater.inflate(R.layout.login, null);
        getWindow().addContentView(loginScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void switchToMainScreen(View view) {
        ((ViewGroup) loginScreen.getParent()).removeView(loginScreen);
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
