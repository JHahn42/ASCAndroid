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

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor;
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
import android.widget.Button;
import android.widget.TextView;

import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
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
    Timer gameLoopTimer;
    TimerTask mainGameLoop;
    private View loginScreen;
    private View inputConfirmationScreen;
    private View endOfDayScreen;
    public JSONArray weatherPolygons;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.login);
        //When button is clicked on loginScreen screen, check if player entered exists
        //If player exists setLoggedIn = true
        Mapbox.getInstance(this, Constants.MAPBOX_API_KEY);
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
                //createGeoJsonSource(style);
                //addPolygonLayer(style);

                ArmchairStormChaser app = (ArmchairStormChaser)getApplication();

                socket = app.getSocket();
                // socket.on(Socket.EVENT_CONNECT, onConnect);
                // socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
                // socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
                // socket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
                socket.on("updatePlayer", onUpdatePlayer);
                socket.on("weatherUpdate", weatherUpdate);
                socket.on("destinationReached", destinationReached);
                socket.on("endOfDay", endOfDay);
                socket.connect();

                map = mapboxMap;

                startGame();
            }

            void startGame() {
                gameLoopTimer = new Timer();
                initializeGameLoop();
                gameLoopTimer.schedule(mainGameLoop, 0, Constants.REFRESH_RATE_IN_SECONDS * 1000);
            }

            void initializeGameLoop() {

                mainGameLoop = new TimerTask() {
                    public void run() {
                        handler.post(new Runnable() {
                            public void run() {
                                //Update player when gameLoopTimer task runs if the player has selected a route and is not selecting a starting location
                                if (inFocus && !isSelectingStartingLocation) {
                                    socket.emit("getPlayerUpdate");
                                    socket.emit("getWeatherUpdate");
                                    updateMarkerPosition();
                                }
                                //If the player has a set route toggle buttons and labels accordingly
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
                //If the player is traveling don't select a new route before stopping the other
                if (!endTravelEnabledDisable) {
                    //If the player isn't selecting a starting location
                    if (!isSelectingStartingLocation) {
                        hasSetRoute = true;
                        //Get coordinates of location clicked
                        destinationLatitude = point.getLatitude();
                        destinationLongitude = point.getLongitude();
                        //Get current location
                        origin = Point.fromLngLat(currentLongitude, currentLatitude);
                        //get destination
                        destination = Point.fromLngLat(destinationLongitude, destinationLatitude);
                        Style style = mapboxMap.getStyle();
                        initSource(style);
                        initLayers(style);
                        //Find route and mark it on map
                        getRoute(style, origin, destination);
                        updateMarkerPosition();
                    } else {
                        //If selecting a startibg location get coordinates of point clicked and set as current
                        currentLatitude = point.getLatitude();
                        currentLongitude = point.getLongitude();
                        //Place marker where current lcoation is selected
                        placeStartingLocationMarker();
                        //Change instruction text to reflect change
                        changeStartingLocationText();
                        //Emit to server where the player now is
//                        socket.emit("selectStartingLocation", currentLatitude, currentLongitude);
                        //Mark player as not selecting a starting location
                        isSelectingStartingLocation = false;
                    }
                }
                return false;
            }
        });
    }

    private void updateWeatherPolygons() {
        Style style = map.getStyle();
        List<List<Point>> POINTS = new ArrayList<>();
        POINTS = createPolygon(POINTS);
//        addIndividualPolygonToMap(style, POINTS);
//        createGeoJsonSource(style);
//        addPolygonLayer(style);
    }

    private List<List<Point>> createPolygon(List<List<Point>> POINTS) {
        List<Point> OUTER_POINTS = new ArrayList<>();
        double polygonLatitude;
        double polygonLongitude;

        //Iterate over every polygon and add coordinates to OUTER_POINTS
        try {
            for (int index = 0; index < weatherPolygons.length(); index++) {
                System.out.println("Weather: " + weatherPolygons.getJSONObject(index).getString("instances"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

//        OUTER_POINTS.add(Point.fromLngLat(polygonLongitude, polygonLatitude));
        //Then add OUTER_POINTS to POINTS
        //Clear OUTER_POINTS
        //Repeat for all polygons


        /*OUTER_POINTS.add(Point.fromLngLat(-122.685699, 45.522585));
        OUTER_POINTS.add(Point.fromLngLat(-122.708873, 45.534611));
        OUTER_POINTS.add(Point.fromLngLat(-122.678833, 45.530883));
        OUTER_POINTS.add(Point.fromLngLat(-122.667503, 45.547115));
        OUTER_POINTS.add(Point.fromLngLat(-122.660121, 45.530643));
        OUTER_POINTS.add(Point.fromLngLat(-122.636260, 45.533529));
        OUTER_POINTS.add(Point.fromLngLat(-122.659091, 45.521743));
        OUTER_POINTS.add(Point.fromLngLat(-122.648792, 45.510677));
        OUTER_POINTS.add(Point.fromLngLat(-122.664070, 45.515008));
        OUTER_POINTS.add(Point.fromLngLat(-122.669048, 45.502496));
        OUTER_POINTS.add(Point.fromLngLat(-122.678489, 45.515369));
        OUTER_POINTS.add(Point.fromLngLat(-122.702007, 45.506346));
        OUTER_POINTS.add(Point.fromLngLat(-122.685699, 45.522585));*/
        POINTS.add(OUTER_POINTS);
        return POINTS;
    }

    private void addIndividualPolygonToMap(Style style, List<List<Point>> POINTS) {
        if (style.getSource("source-id") != null ) {
            style.removeSource("source-id");
        }
        style.addSource(new GeoJsonSource("source-id", Polygon.fromLngLats(POINTS)));
        style.addLayer(new FillLayer("layer-id", "source-id").withProperties(
                fillColor(Color.parseColor("#3bb2d0"))));
    }

    /*private void createGeoJsonSource(@NonNull Style style) {
//        Object geoJSON = makeGeoJSON(weatherPolygons);
//        style.addSource(new GeoJsonSource(GEOJSON_SOURCE_ID, geoJSON));
//        loadedMapStyle.addSource(new GeoJsonSource(GEOJSON_SOURCE_ID, loadJsonFromAsset("Tornado_Watch.geojson")));
        *//*CustomGeometrySource source = new CustomGeometrySource("geojson-source", (FeatureCollection.fromJson(loadJsonFromAsset("Tornado_Watch.geojson"))));
        loadedMapStyle.addSource(source);*//*
    }*/



    /*private void addPolygonLayer(@NonNull Style loadedMapStyle) {
        FillLayer countryPolygonFillLayer = new FillLayer("polygon", GEOJSON_SOURCE_ID);
        countryPolygonFillLayer.setProperties(
                PropertyFactory.fillOpacity(.4f));
        countryPolygonFillLayer.setFilter(eq(literal("$type"), literal("Polygon")));
        loadedMapStyle.addLayer(countryPolygonFillLayer);
    }*/


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
                iconAllowOverlap(false),
                iconIgnorePlacement(true)
        ));
    }

    private void changeStartingLocationText() {
        //Get select route textView from UI
        TextView selectRoute = findViewById(R.id.textView_selectNewRoute);
        //Change select route label
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
        LineLayer routeLayer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                iconAllowOverlap(true)
        );

        routeLayer.setProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(5f),
                lineColor(Color.parseColor("#6699ff"))
        );

        if (loadedMapStyle.getLayer(ROUTE_LAYER_ID) == null) {
            loadedMapStyle.addLayer(routeLayer);
        }

        /*if (loadedMapStyle.getLayer(ICON_LAYER_ID) == null) {
            loadedMapStyle.addLayer(new SymbolLayer(ICON_LAYER_ID, ICON_SOURCE_ID).withProperties(
                    iconImage(RED_PIN_ICON_ID),
                    iconIgnorePlacement(true),
                    iconIgnorePlacement(true),
                    iconOffset(new Float[]{0f, -4f})));
        }*/
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
                System.out.println(call.request().url().toString());
                Timber.d("Response code: %s", response.code());
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
                Timber.e("Error: %s", throwable.getMessage());
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



    /*private String loadJsonFromAsset(String filename) {
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
    }*/

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
                    JSONObject data = (JSONObject) args[0];
                    JSONArray storms;
                    try{
                        storms = data.getJSONArray("storms");
                        weatherPolygons = storms;
//                        updateWeatherPolygons();
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
                        sendNotificationToPhone();
                    }
                }
            });
        }
    };

    private void sendNotificationToPhone() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notify=new Notification.Builder
                (getApplicationContext()).setContentTitle("Test").setContentText("Congratulations").
                setContentTitle("You have reached your destination!").setSmallIcon(R.drawable.asc_logo_small).build();
        notify.flags |= Notification.FLAG_AUTO_CANCEL;
        assert notificationManager != null;
        notificationManager.notify(0, notify);
    }

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
                    //Set values for end of day screen
                    setScoreOnEndOfDayScreen(dailyScore, totalScore);
                    //Switch view to end of day screen
                    switchToEndOfDayScreen();
                }
            });
        }
    };

    private void setScoreOnEndOfDayScreen(int dailyScore, int totalScore) {
        //Get daily score and total score textViews from UI
        TextView dailyScoreText = findViewById(R.id.dailyScore_textView);
        TextView totalScoreText = findViewById(R.id.totalScore_textView);
        //Set text for daily score and total score on end of day screen to score received from server
        dailyScoreText.setText(Integer.toString(dailyScore));
        totalScoreText.setText(Integer.toString(totalScore));
    }

    public void beginNewDay(View view) {
        //Remove end of day screen if the beginning of day has begun
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
        //Get time left textView from Ui
        TextView timeText = findViewById(R.id.textView_Time);
        //Set text from time left to time left updated from server
        timeText.setText(Integer.toString((int) floor(timeLeft)) + " Seconds");
    }

    private void updateScore(int score) {
        //Get score textView from UI
        TextView scoreText = findViewById(R.id.textView_Score);
        //Set text for score to score updated from server
        scoreText.setText(Integer.toString(score));
    }

    public void updateLocation(Point currentLocationFromServer) {
        //Set current location, current latitude and current longitude to information from server
        currentLongitude = currentLocationFromServer.longitude();
        currentLatitude = currentLocationFromServer.latitude();
        currentLocation = Point.fromLngLat(currentLongitude, currentLatitude);
    }

    private void updateMarkerPosition() {
        //Move the current location marker to the updated current location position from the server
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
        //When the stop travel button ic clicked, display in put confirmation screen
        LayoutInflater inflater = getLayoutInflater();
        inputConfirmationScreen = inflater.inflate(R.layout.input_confirmation, null);
        getWindow().addContentView(inputConfirmationScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void toggleStopTravelButton(boolean enableDisable) {
        int toggleButton;
        int toggleText;
        //Get stopTravel button from UI
        Button stopTravel = findViewById(R.id.button_StopTravel);
        //Get selectRoute label from UI
        TextView selectRoute = findViewById(R.id.textView_selectNewRoute);
        if (enableDisable) {
            //If the player is traveling set button to visible and label to invisible
            toggleButton = View.VISIBLE;
            toggleText = View.INVISIBLE;
        } else {
            //If the player in't traveling set button to invisible and label to visible
            toggleButton = View.INVISIBLE;
            toggleText = View.VISIBLE;
        }
        //Set buttons and text visibility
        stopTravel.setVisibility(toggleButton);
        selectRoute.setVisibility(toggleText);
    }

    public void stopTravelButtonNo(View view){
        //If player selects no, remove input confirmation screen
        removeInputConfirmationScreen();
    }

    public void stopTravelButtonYes(View view){
        //If the player selects yes on the input confirmation screen emit sopt travel
        socket.emit("stopTravel");
        //Remove current route from screen
        removeRoute();
        //Set time left to 0
        updateTimeLeft(0);
        //Mark that the player doesn't have a set route now
        hasSetRoute = false;
        //Remove input confirmation screen
        removeInputConfirmationScreen();
    }

    private void removeInputConfirmationScreen(){
        ((ViewGroup) inputConfirmationScreen.getParent()).removeView(inputConfirmationScreen);
    }

    public void login(View view) {
        //Get the textView for username and password from UI
        TextView usernameTextBox = findViewById(R.id.userName_text_input);
        TextView passwordTextBox = findViewById(R.id.password_text_input);
        //If both input are not null
        if (usernameTextBox.getText() != null && passwordTextBox.getText() != null) {
            //Get the text for username and password
            String usernameTextInput = usernameTextBox.getText().toString();
            String passwordTextInput = passwordTextBox.getText().toString();
            //Check if player exists
            /*if (userExists(usernameTextInput, passwordTextInput)) {
                socket.emit("login", usernameTextInput, Point.fromLngLat(currentLongitude, currentLatitude), 0, 0, 0);
                loggedIn = true;
            }*/
            //If the player exists get player information and remove login screen
        }
    }

    public void logout(View view) {
//        socket.emit("logoff");
        switchToLoginScreen(view);
    }

    /*private boolean userExists(String usernameTextInput, String passwordTextInput) {
        //Replace with checking "Database/Data"
        return true;
    }*/

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
