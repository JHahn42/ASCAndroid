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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;
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
import com.mapbox.geojson.MultiPolygon;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
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
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MapboxMap map;
    private MapView mapView;
    private double currentLatitude;
    private double currentLongitude;
    private double destinationLatitude;
    private double destinationLongitude;
    private static final String ROUTE_LAYER_ID = "route-layer-id";
    private static final String ROUTE_SOURCE_ID = "route-source-id";
    private static final String ICON_SOURCE_ID = "icon-source-id";
    private DirectionsRoute currentRoute;
    private Point origin;
    private Point destination;
    private SymbolLayer originMarkerSymbolLayer = null;
    private GeoJsonSource originMarkerGeoJsonSource = null;
    private SymbolLayer destinationMarkerSymbolLayer = null;
    private GeoJsonSource destinationMarkerGeoJsonSource = null;
    final Handler handler = new Handler();
    Timer gameLoopTimer;
    TimerTask mainGameLoop;
    private View loginScreen;
    private View inputConfirmationScreen;
    private View endOfDayScreen;
    private View howToPlayScreen;

//    private boolean isMarkers = false;
//    private boolean isWeather = false;
//    private boolean isDestinationMarkers = false;
    private boolean hasSetRoute = false;
    private boolean loggedIn = false;
    private boolean inFocus = true;
    private boolean endTravelEnabledDisable = false;
    public boolean isEndOfDay = false;
    public boolean isSelectingStartingLocation = true;

    private double scoreMultiplier = 1;
    private boolean continueFromLastLoc = false;

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

                addLoginScreen();

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
                if (!endTravelEnabledDisable && inFocus) {
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
                        setDestinationMarker(destinationLongitude, destinationLatitude);
                        updateMarkerPosition();
                    } else {
                        //If the user has logged in
                        if (loggedIn) {
                            //If selecting a starting location get coordinates of point clicked and set as current
                            currentLatitude = point.getLatitude();
                            currentLongitude = point.getLongitude();
                            //Place marker where current lcoation is selected
                            placeStartingLocationMarker();
                            //Change instruction text to reflect change
                            changeStartingLocationText();
                            //Emit to server where the player now is
                            if(continueFromLastLoc) {
                                scoreMultiplier = 1.2;
                            }
                            else {
                                scoreMultiplier = 1;
                            }
                            socket.emit("startLocationSelect", currentLatitude, currentLongitude, scoreMultiplier);
                            //Mark player as not selecting a starting location
                            isSelectingStartingLocation = false;
                        }
                    }
                }
                return false;
            }
        });
    }

    private void setDestinationMarker(double destinationLongitude, double destinationLatitude) {
        Style style = map.getStyle();

        if (destinationMarkerSymbolLayer != null) {
            removeDestinationMarker();
        }

        style.addImage("destination-marker-icon-id",
                BitmapFactory.decodeResource(
                        MainActivity.this.getResources(), R.drawable.asc_destination_marker));
        destinationMarkerGeoJsonSource = new GeoJsonSource("destination-source-id", Feature.fromGeometry(
                Point.fromLngLat(destinationLongitude, destinationLatitude)));
        style.addSource(destinationMarkerGeoJsonSource);

        destinationMarkerSymbolLayer = new SymbolLayer("destinationMarker-layer-id", "destination-source-id");
        destinationMarkerSymbolLayer.withProperties(
                PropertyFactory.iconImage("destination-marker-icon-id")
        );
        style.addLayer(destinationMarkerSymbolLayer.withProperties(
                iconAllowOverlap(false),
                iconIgnorePlacement(true)
        ));
    }

    private void removeDestinationMarker() {
        Style style = map.getStyle();
        style.removeLayer("destinationMarker-layer-id");
        style.removeSource("destination-source-id");
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
        LineLayer routeLayer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID);

        routeLayer.setProperties(
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineWidth(5f),
                lineColor(Color.parseColor(Constants.ROUTE_LINE_COLOR))
        );

        if (loadedMapStyle.getLayer(ROUTE_LAYER_ID) == null) {
            loadedMapStyle.addLayer(routeLayer);
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
                        if(wind.length() > 0) {
                            windPoints = fillPointStorm(wind);
                            addPointLayer(map.getStyle(), "wind_layer", "wind_source", windPoints, "#199606");
                        }

                        JSONArray tornado = weather.getJSONArray(5);
                        if(tornado.length() > 0) {
                            tornadoPoints = fillPointStorm(tornado);
                            addPointLayer(map.getStyle(), "tornado_layer", "tornado_source", tornadoPoints, "#960606");
                        }

                        JSONArray hail = weather.getJSONArray(6);
                        if(hail.length() > 0) {
                            fillHailStorm(hail);
                            if(!hailSmall.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_small_layer", "hail_small_source", hailSmall, "#b0a5ff");
                            }
                            if(!hailOneInch.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_one_layer", "hail_one_source", hailOneInch, "#8173ef");
                            }
                            if(!hailTwoInch.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_two_layer", "hail_two_source", hailTwoInch, "#5545d1");
                            }
                            if(!hailThreeInch.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_three_layer", "hail_three_source", hailThreeInch, "#140587");
                            }
                        }


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

    private void fillHailStorm(JSONArray hail) throws JSONException {
        hailSmall.clear();
        hailOneInch.clear();
        hailTwoInch.clear();
        hailThreeInch.clear();

        for(int i = 0; i < hail.length(); i++) {
            JSONArray coordinates = hail.getJSONObject(i).getJSONArray("coordinates");
            int size = hail.getJSONObject(i).getInt("Size");
            if(size < 100) {
                hailSmall.add(Feature.fromGeometry(Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))));
            }
            else if (size >= 100 && size < 200) {
                hailOneInch.add(Feature.fromGeometry(Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))));
            }
            else if (size >= 200 && size < 300) {
                hailTwoInch.add(Feature.fromGeometry(Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))));
            }
            else if (size >= 300) {
                hailThreeInch.add(Feature.fromGeometry(Point.fromLngLat(coordinates.getDouble(0), coordinates.getDouble(1))));
            }
        }

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
                        sendNotificationToPhone();
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

    public void beginNewDayNewStart(View view) {
        //Set to selecting starting location
        isSelectingStartingLocation = true;
        //Remove Marker from Screen
        //Reset Score Multiplier; Emit to Server
        //Remove end of day screen if the beginning of day has begun
        ((ViewGroup) endOfDayScreen.getParent()).removeView(endOfDayScreen);
    }

    public void beginNewDaySameStart(View view) {
        //Remove end of day screen if the beginning of day has begun
        //Change Score Multiplier; Emit to Server
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

    private void sendNotificationToPhone() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notify=new Notification.Builder
                (getApplicationContext()).setContentTitle("Test").setContentText("Congratulations").
                setContentTitle("You have reached your destination!").setSmallIcon(R.drawable.asc_logo_small).build();
        notify.flags |= Notification.FLAG_AUTO_CANCEL;
        assert notificationManager != null;
        notificationManager.notify(0, notify);
    }

    public void stopTravel(View view) {
        //If in focus (No Other screens on top)
        if (inFocus) {
            //When the stop travel button ic clicked, display in put confirmation screen
            LayoutInflater inflater = getLayoutInflater();
            inputConfirmationScreen = inflater.inflate(R.layout.input_confirmation, null);
            getWindow().addContentView(inputConfirmationScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
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
        removeDestinationMarker();
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
            loggedIn = true;
            switchToMainScreen(view);
        }
    }

    public void logout(View view) {
//        socket.emit("logoff");
        switchToLoginScreen(view);
    }

    public void switchToHowToPlayScreen(View view) {
        if (inFocus) {
            inFocus = false;
            LayoutInflater inflater = getLayoutInflater();
            howToPlayScreen = inflater.inflate(R.layout.how_to_play, null);
            getWindow().addContentView(howToPlayScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    public void removeHowToPlayScreen(View view){
        inFocus = true;
        ((ViewGroup) howToPlayScreen.getParent()).removeView(howToPlayScreen);
    }

    public void switchToLoginScreen(View view) {
        if (inFocus) {
            addLoginScreen();
        }
    }

    private void addLoginScreen(){
        inFocus = false;
        LayoutInflater inflater = getLayoutInflater();
        loginScreen = inflater.inflate(R.layout.login, null);
        getWindow().addContentView(loginScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void switchToMainScreen(View view) {
        inFocus = true;
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
