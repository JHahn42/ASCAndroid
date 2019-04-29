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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

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

    private Point origin;
    private double currentLatitude;
    private double currentLongitude;
    public Point currentLocation = Point.fromLngLat(currentLongitude, currentLatitude);
    private double destinationLatitude;
    private double destinationLongitude;
    private Point destination;

    private static final String ROUTE_LAYER_ID = "route-layer-id";
    private static final String ROUTE_SOURCE_ID = "route-source-id";
    private static final String ICON_SOURCE_ID = "icon-source-id";
    private GeoJsonSource originMarkerGeoJsonSource = null;
//    private SymbolLayer destinationMarkerSymbolLayer = null;
    private DirectionsRoute currentRoute;

    final Handler handler = new Handler();
    Timer gameLoopTimer;
    TimerTask mainGameLoop;

    LayoutInflater inflater;

    private View loginScreen;
    private View inputConfirmationScreen;
    private View endOfDayScreen;
    private View howToPlayScreen;

    private boolean mapInFocus = true;
    private boolean appInFocus = true;
    private boolean isTraveling = false;
    public boolean isEndOfDay = false;
    public boolean isSelectingStartingLocation = true;
    private boolean showBeginOfDay = false;

    private String currentUsername;
    private String currentKey;
    private int indexOfPlayer;
    private double scoreMultiplier = 1;
    private int dailyScore = 0;
    private int totalScore = 0;
    private String routeFromServer;

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

    private ArrayList<ArrayList<String>> playersList = new ArrayList<ArrayList<String>>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // holds the socket
        ArmchairStormChaser app = (ArmchairStormChaser)getApplication();

        // create all of the app's other pages
        inflater = getLayoutInflater();

        loginScreen = inflater.inflate(R.layout.login, null);
        inputConfirmationScreen = inflater.inflate(R.layout.input_confirmation, null);
        endOfDayScreen = inflater.inflate(R.layout.end_of_day_screen, null);
        howToPlayScreen = inflater.inflate(R.layout.how_to_play, null);


        socket = app.getSocket();
        // socket.on(Socket.EVENT_CONNECT, onConnect);
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        // socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        // socket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        socket.on("errorMessage", onError);
        socket.on("updatePlayer", onUpdatePlayer);
        socket.on("destinationReached", destinationReached);
        socket.on("endOfDay", endOfDay);
        socket.on("weatherUpdate", weatherUpdate);
        socket.on("loginFromPrevious", loginFromPrevious);
        socket.on("loginSuccess", loginSuccess);
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
                gameLoopTimer.schedule(mainGameLoop, 5000, Constants.REFRESH_RATE_IN_SECONDS * 1000);
            }

            void initializeGameLoop() {
                mainGameLoop = new TimerTask() {
                    public void run() {
                        handler.post(new Runnable() {
                            public void run() {
                                //Update player when gameLoopTimer task runs if the player has selected a route and is not selecting a starting location
                                if (mapInFocus && appInFocus && !isEndOfDay && !isSelectingStartingLocation) {
                                    socket.emit("getPlayerUpdate");
                                }
                                //Enable or Disable End of Day buttons based on Time
                                //toggleEndOfDayButtons();
                                //If the player has a set route toggle buttons and labels accordingly
                                toggleStopTravelButton(isTraveling);
                            }
                        });
                    }
                };
            }
        });

        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(@NonNull LatLng point) {
                //If the player is traveling don't select a new route before stopping the other and map is in focus
                if (mapInFocus && !isTraveling && !isEndOfDay) {
                    //If the player is selecting a new route
                    if (!isSelectingStartingLocation) {
                        isTraveling = true;
                        //Get coordinates of location clicked
                        destinationLatitude = point.getLatitude();
                        destinationLongitude = point.getLongitude();
                        //Get current location
                        origin = Point.fromLngLat(currentLongitude, currentLatitude);
                        //get destination
                        destination = Point.fromLngLat(destinationLongitude, destinationLatitude);
                        //Find route and mark it on map
                        getRoute(origin, destination);
//                        setDestinationMarker(destinationLongitude, destinationLatitude);
                        updateMarkerPosition();
                        toggleStopTravelButton(true);
                    } else {
                        //If selecting a starting location get coordinates of point clicked and set as current
                        currentLatitude = point.getLatitude();
                        currentLongitude = point.getLongitude();
                        currentLocation = Point.fromLngLat(currentLongitude, currentLatitude);
                        //Place marker where current lcoation is selected
                        placeStartingLocationMarker();
                        //Change instruction text to reflect change
                        changeStartingLocationText();
                        //Emit to server where the player now is
                        socket.emit("startLocationSelect", currentLongitude, currentLatitude, scoreMultiplier);
                        //Mark player as not selecting a starting location
                        isSelectingStartingLocation = false;
                    }
                }
                return false;
            }
        });
    }

    //Marker Methods

    /*private void setDestinationMarker(double destinationLongitude, double destinationLatitude) {
        Style style = map.getStyle();
        if (destinationMarkerSymbolLayer != null) {
            removeDestinationMarker();
        }
        style.addImage("destination-marker-icon-id",
                BitmapFactory.decodeResource(
                        MainActivity.this.getResources(), R.drawable.asc_destination_marker));
        GeoJsonSource destinationMarkerGeoJsonSource = new GeoJsonSource("destination-source-id", Feature.fromGeometry(
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
    }*/

    private void placeStartingLocationMarker() {
        Style style = map.getStyle();
        style.addImage("origin-marker-icon-id",
                BitmapFactory.decodeResource(
                        MainActivity.this.getResources(), R.drawable.asc_logo_small));
        originMarkerGeoJsonSource = new GeoJsonSource("origin-source-id", Feature.fromGeometry(
                Point.fromLngLat(currentLongitude, currentLatitude)));
        style.addSource(originMarkerGeoJsonSource);
        SymbolLayer originMarkerSymbolLayer = new SymbolLayer("originMarker-layer-id", "origin-source-id");
        originMarkerSymbolLayer.withProperties(
                PropertyFactory.iconImage("origin-marker-icon-id")
        );
        style.addLayer(originMarkerSymbolLayer.withProperties(
                iconAllowOverlap(false),
                iconIgnorePlacement(true)
        ));
    }

    private void removeDestinationMarker() {
        Style style = map.getStyle();
        style.removeLayer("destinationMarker-layer-id");
        style.removeSource("destination-source-id");
    }

    private void removeOriginMarker() {
        Style style = map.getStyle();
        style.removeLayer("originMarker-layer-id");
        style.removeSource("origin-source-id");
    }

    private void changeStartingLocationText() {
        //Get select route textView from UI
        TextView selectRoute = findViewById(R.id.textView_selectNewRoute);
        //Change select route label
        selectRoute.setText(getApplicationContext().getResources().getString(R.string.selectNewRouteLabel));
    }

    private void changeStartingLocationTextBack() {
        //Get select route textView from UI
        TextView selectRoute = findViewById(R.id.textView_selectNewRoute);
        //Change select route label
        selectRoute.setText(getApplicationContext().getResources().getString(R.string.selectStartingLocationLabel));
    }

    /////////////////////////////////////////////

    //Route

    private void getRoute(Point origin, Point destination) {
        Style style = map.getStyle();
        initSource(style);
        initLayers(style);
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
                socket.emit("setTravelRoute", currentRoute.geometry(), currentRoute.distance(), currentRoute.duration(), destinationLongitude, destinationLatitude);
                addRouteToStyle(currentRoute.geometry());
            }
            @Override
            public void onFailure(@NonNull Call<DirectionsResponse> call, @NonNull Throwable throwable) {
                Timber.e("Error: %s", throwable.getMessage());
            }
        });
    }

    private void addRouteToStyle(String geometryRoute) {
        Style style = map.getStyle();
        if (style.isFullyLoaded()) {
            GeoJsonSource source = style.getSourceAs(ROUTE_SOURCE_ID);
            if (source != null) {
                Timber.d("onResponse: source != null");
                source.setGeoJson(FeatureCollection.fromFeature(
                        Feature.fromGeometry(LineString.fromPolyline(Objects.requireNonNull(geometryRoute), PRECISION_6))));
            }
        }
    }

    private void removeRoute() {
        Style style = map.getStyle();
        assert style != null;
        style.removeLayer(ROUTE_LAYER_ID);
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

    private void initSourceFromServer(@NonNull Style loadedMapStyle) {
        if (loadedMapStyle.getSourceAs(ROUTE_SOURCE_ID) == null) {
            loadedMapStyle.addSource(new GeoJsonSource(ROUTE_SOURCE_ID,
                    FeatureCollection.fromFeatures(new Feature[]{})));
        }
        GeoJsonSource iconGeoJsonSource = new GeoJsonSource(ICON_SOURCE_ID, FeatureCollection.fromFeatures(new Feature[] {
                Feature.fromGeometry(Point.fromLngLat(currentLongitude, currentLatitude)),
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
            loadedMapStyle.addLayerBelow(routeLayer, "originMarker-layer-id");
        }
    }

    /////////////////////////////////////////////////

    //Draw Weather on Map

    private void addPolygonLayer(@NonNull Style loadedMapStyle, String layerId, String sourceId, MultiPolygon polygons, String color) {
        GeoJsonSource source = loadedMapStyle.getSourceAs(sourceId);
        if(source != null) {
            loadedMapStyle.removeLayer(layerId);
            loadedMapStyle.removeSource(sourceId);
        }
        loadedMapStyle.addSource(new GeoJsonSource(sourceId, polygons));
        loadedMapStyle.addLayer(new FillLayer(layerId, sourceId).withProperties(
                PropertyFactory.fillColor(Color.parseColor(color)),PropertyFactory.fillOpacity(.7f),PropertyFactory.fillOutlineColor(Color.parseColor("#000000"))
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

    ////////////////////////////////////////////////

    //Listeners

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
                    double timeLeft;
                    JSONArray latlong;
                    try{
                        latlong = data.getJSONArray("currentLocation");
                        currentLocation = Point.fromLngLat(latlong.getDouble(0), latlong.getDouble(1));
                        dailyScore = data.getInt("currentScore");
                        totalScore = data.getInt("totalScore");
                        timeLeft = data.getDouble("timeLeft");
                        // update saved long for active player
                        playersList.get(indexOfPlayer).set(2, latlong.getString(0));
                        // update saved lat for active player
                        playersList.get(indexOfPlayer).set(3, latlong.getString(1));
                        playersList.get(indexOfPlayer).set(4, Integer.toString(dailyScore));
                        playersList.get(indexOfPlayer).set(5, Integer.toString(totalScore));

                        updateLocation(currentLocation);
                        updateTimeLeft(timeLeft);
                        updateScore(dailyScore);
                        updateMarkerPosition();
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
                            addPolygonLayer(map.getStyle(), "tornado_warnings_layer", "tornado_warnings_source", tornadoWarnings, "#ff0000");
                        }
                        JSONArray tornWatch = weather.getJSONArray(1);
                        if(tornWatch.length() > 0) {
                            tornadoWatches = fillStorm(tornWatch);
                            addPolygonLayer(map.getStyle(), "tornado_watch_layer", "tornado_watch_source", tornadoWatches, "#f8ff29");
                        }
                        JSONArray tsWarn = weather.getJSONArray(2);
                        if(tsWarn.length() > 0) {
                            tsWarnings = fillStorm(tsWarn);
                            addPolygonLayer(map.getStyle(), "thunderstorm_warning_layer", "thunderstorm_warning_source", tsWarnings, "#0eaa0e");
                        }
                        JSONArray tsWatch = weather.getJSONArray(3);
                        if(tsWatch.length() > 0) {
                            tsWatches = fillStorm(tsWatch);
                            addPolygonLayer(map.getStyle(), "thunderstorm_watch_layer", "thunderstorm_watch_source", tsWatches, "#33f1ff");
                        }
                        JSONArray wind = weather.getJSONArray(4);
                        if(wind.length() > 0) {
                            windPoints = fillPointStorm(wind);
                            addPointLayer(map.getStyle(), "wind_layer", "wind_source", windPoints, "#64fe10");
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
                                addPointLayer(map.getStyle(), "hail_small_layer", "hail_small_source", hailSmall, "#80ffdf");
                            }
                            if(!hailOneInch.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_one_layer", "hail_one_source", hailOneInch, "#80bfff");
                            }
                            if(!hailTwoInch.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_two_layer", "hail_two_source", hailTwoInch, "#0075eb");
                            }
                            if(!hailThreeInch.isEmpty()) {
                                addPointLayer(map.getStyle(), "hail_three_layer", "hail_three_source", hailThreeInch, "#1400eb");
                            }
                        }
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
                    removeRoute();
                    if(!appInFocus) {
                        sendNotificationToPhone();
                    }
                    isTraveling = false;
                }
            });
        }
    };

    private Emitter.Listener loginFromPrevious = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    try {
                        dailyScore = data.getInt("dailyScore");
                        totalScore = data.getInt("totalScore");
                        currentLongitude = data.getDouble("currentLon");
                        currentLatitude = data.getDouble("currentLat");
                        currentLocation = Point.fromLngLat(currentLongitude, currentLatitude);
                        routeFromServer = data.getString("routeGeometry");
                        isTraveling = data.getBoolean("isTraveling");
                        removeOriginMarker();
                        placeStartingLocationMarker();
                        Style style = map.getStyle();
                        if (isTraveling) {
                            destinationLongitude = data.getDouble("destLon");
                            destinationLatitude =  data.getDouble("destLat");
                            destination = Point.fromLngLat(destinationLongitude, destinationLatitude);
                            initSourceFromServer(style);
                            initLayers(style);
                            addRouteToStyle(routeFromServer);
                        }
                        isSelectingStartingLocation = false;
                        changeStartingLocationText();
//                        setDestinationMarker();
                        removeLoginScreen();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    };

    private Emitter.Listener loginSuccess = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    removeLoginScreen();
                    if(showBeginOfDay) {
                        switchToEndOfDayScreen();
                        //Set Screen to Say Beginning of Day
                        setBeginOfDayText();
                        setEndOfDayScreenButtons(true);
                        setScoreOnEndOfDayScreen(dailyScore, totalScore);
                        dailyScore = 0;
                    }
                    else {
                        isSelectingStartingLocation = true;
                    }
                }
            });
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logout(loginScreen);
                }
            });
        }
    };

    private Emitter.Listener endOfDay = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //socket.emit("getPlayerUpdate");
                    isEndOfDay = true;
                    if (loginScreen.hasFocus()) {
//                        removeLoginScreen();
                        toggleHideUserNameInput(false);
                    }
                    //Switch view to end of day screen
                    switchToEndOfDayScreen();
                    //Set Text to End Of Day
                    setEndOfDayText();
                    //Set Buttons Disabled
                    setEndOfDayScreenButtons(false);
                    //Set values for end of day screen
                    setScoreOnEndOfDayScreen(dailyScore, totalScore);
                }
            });
        }
    };

    // prints error message to console when an illegal action is picked up by the server.
    private Emitter.Listener onError = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    try {
                        String error = data.getString("errorMessage");
                        System.out.println(error);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    };

    ////////////////////////////////////////////////


    //All End Of Day Screen methods

    private void setScoreOnEndOfDayScreen(int dailyScore, int totalScore) {
        //Get daily score and total score textViews from UI
        TextView dailyScoreText = findViewById(R.id.dailyScore_textView);
        TextView totalScoreText = findViewById(R.id.totalScore_textView);
        //Set text for daily score and total score on end of day screen to score received from server
        dailyScoreText.setText(Integer.toString(dailyScore));
        totalScoreText.setText(Integer.toString(totalScore));
    }

    private void setEndOfDayScreenButtons(Boolean toggle){
        findViewById(R.id.beginNewDay_SameLocation_button).setEnabled(toggle);
        findViewById(R.id.beginNewDay_NewStart_button).setEnabled(toggle);
    }

    private void setBeginOfDayText() {
        //Get End Of Day Label from UI
        TextView endOfDayLabel = findViewById(R.id.endofDay_textView);
        //Set text to Beginning of Day
        endOfDayLabel.setText("Beginning of Day");
    }

    private void setEndOfDayText() {
        //Get End Of Day Label from UI
        TextView endOfDayLabel = findViewById(R.id.endofDay_textView);
        //Set text to Beginning of Day
        endOfDayLabel.setText("End of Day");
    }

    public void beginNewDayNewStart(View view) {
        //Set to selecting starting location
//        isSelectingStartingLocation = true;
        //Remove Marker from Screen
        //Reset Score Multiplier;
        isSelectingStartingLocation = true;
        scoreMultiplier = 1;
        //Remove end of day screen if the beginning of day has begun
//        if (endOfDayScreen.hasFocus()) {
        ((ViewGroup) endOfDayScreen.getParent()).removeView(endOfDayScreen);
        //Change text to Say choose new starting location
        changeStartingLocationTextBack();
        mapInFocus = true;
//        }
    }

    public void beginNewDaySameStart(View view) {
        //Remove end of day screen if the beginning of day has begun
        //Change Score Multiplier;
        isSelectingStartingLocation = false;
        scoreMultiplier = 1.2;
        changeStartingLocationText();
        socket.emit("startLocationSelect", currentLongitude, currentLatitude, scoreMultiplier);
//        if (endOfDayScreen.hasFocus()) {
        ((ViewGroup) endOfDayScreen.getParent()).removeView(endOfDayScreen);
        //Add Starting Location to where they are
        placeStartingLocationMarker();
        mapInFocus = true;
//        }
    }

    /////////////////////////////////////////////////

    //Update Methods

    private void updateTimeLeft(double timeLeft) {
        //Get time left textView from Ui
        TextView timeText = findViewById(R.id.textView_Time);
        //Set text from time left to time left updated from server
        int timesec = (int)Math.round(timeLeft);
        int hours = timesec / 3600;
        int minutes = (timesec % 3600) / 60;
        int seconds = (timesec % 3600) % 60;
        if (timeIsNegative(seconds)) {
            seconds = 0;
        }
        String countDown = String.format("%2d:%02d:%02d", hours, minutes, seconds);
        timeText.setText(countDown);
        timeText.setBackgroundColor(0xffffffff);
    }

    private boolean timeIsNegative(int seconds) {
        return seconds < 0;
    }

    private void updateScore(int score) {
        //Get score textView from UI
        TextView scoreText = findViewById(R.id.textView_Score);
        //Set text for score to score updated from server
        scoreText.setText(Integer.toString(score));
        scoreText.setBackgroundColor(0xffffffff);
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

    ///////////////////////////////////////////////////////

    //Misc Methods

    private void sendNotificationToPhone() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notify = new Notification.Builder
                (getApplicationContext()).setContentTitle("Armchair Stormchasers").setContentText("Congratulations").
                setContentTitle("You have reached your destination!").setSmallIcon(R.drawable.asc_logo_small).build();
        notify.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(0, notify);
    }

    private void removeNonWeatherFromStyle() {
        removeOriginMarker();
        removeDestinationMarker();
        removeRoute();
    }

    private void resetConditions() {
        currentLocation = null;
        mapInFocus = false;
        isTraveling = false;
        isEndOfDay = false;
        changeStartingLocationTextBack();
    }

    /////////////////////////////////////////////////////////

    //Login

    public void login(View view) {
        //Disable Login Button
        toggleLoginButton(true);
        //Get the textView for username from UI
        TextView usernameTextBox = findViewById(R.id.userName_text_input);
        //If input is not null
        if (usernameTextBox.getText() != null && loginScreen.hasFocus()) {
            // get username and strip out unwanted special characters
            currentUsername = usernameTextBox.getText().toString().replaceAll("[^a-zA-Z0-9\\s_-]", "");
            Log.d("LOGIN", currentUsername);
            // check if player file has not already been read
            if (playersList.isEmpty()) {

                File playerFile = new File(getApplicationContext().getFilesDir(), "player_profiles");
                Log.d("FILELOC", playerFile.toString());
                // check if file exists. If not, create player and fill with default info
                if (!playerFile.exists()) {
                    try {
                        Log.d("FILELOC", "Don't exist");
                        ArrayList<String> playerInfo = new ArrayList<String>();
                        playerInfo.add(currentUsername);
                        // a unique key
                        currentKey = UUID.randomUUID().toString();
                        playerInfo.add(currentKey);
                        playerInfo.add("-85.407286"); // current longitude
                        playerInfo.add("40.203264"); // current latitude
                        playerInfo.add("0"); // daily score value
                        playerInfo.add("0"); // total score

                        currentLongitude = -85.407286;
                        currentLatitude = 40.203264;
                        dailyScore = 0;
                        totalScore = 0;

                        String playerOutput = "";

                        for(int i = 0; i < playerInfo.size(); i++){
                            playerOutput += playerInfo.get(i) + ",";
                        }
                        playerOutput = playerOutput.substring(0, playerOutput.length() - 1);
                        playerOutput += ";";
                        Log.d("FILELOC", playerOutput);
                        FileOutputStream fos = openFileOutput("player_profiles", Context.MODE_PRIVATE);
                        fos.write(playerOutput.getBytes());
                        fos.close();
                        playersList.add(playerInfo);
                        indexOfPlayer = 0;
                    } catch(IOException e){
                        e.printStackTrace();
                    }

                } // file does exist
                else {
                    Log.d("FILELOC", "FILELOC DO EXIST");
                    String line = null;
                    try {
                        FileInputStream fileInputStream = new FileInputStream(playerFile);
                        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                        StringBuilder stringBuilder = new StringBuilder();

                        while ( (line = bufferedReader.readLine()) != null )
                        {
                            stringBuilder.append(line);
                        }
                        fileInputStream.close();
                        line = stringBuilder.toString();

                        bufferedReader.close();

                        Log.d("FILELOC", line);

                        String[] profiles = line.split(";");


                        // fill playerslist with saved profiles from file
                        for(int i = 0; i < profiles.length; i++) {
                            String[] values = profiles[i].split(",");
                            // if values isn't an empty string
                            if (values.length > 1) {
                                ArrayList<String> playerProfile = new ArrayList<String>();
                                for (int k = 0; k < values.length; k++) {
                                    playerProfile.add(values[k]);
                                }
                                Log.d("PROFILES", profiles[i]);
                                playersList.add(playerProfile);
                            }
                        }
                        boolean foundPlayerProfile = false;
                        // check player list for current username
                        for(int i = 0; i < playersList.size(); i++)  {
                            if(currentUsername.equals(playersList.get(i).get(0))) {
                                currentKey = playersList.get(i).get(1);
                                currentLongitude = Double.parseDouble(playersList.get(i).get(2));
                                currentLatitude = Double.parseDouble(playersList.get(i).get(3));
                                dailyScore = Integer.parseInt(playersList.get(i).get(4));
                                totalScore = Integer.parseInt(playersList.get(i).get(5));
                                indexOfPlayer = i;
                                foundPlayerProfile = true;
                                showBeginOfDay = true;
                                break;
                            }
                        } // if not found create new user profile
                        if(!foundPlayerProfile) {
                            ArrayList<String> newPlayer = new ArrayList<String>();
                            newPlayer.add(currentUsername);
                            currentKey = UUID.randomUUID().toString();
                            newPlayer.add(currentKey);
                            newPlayer.add("-85.407286");
                            newPlayer.add("40.203264");
                            newPlayer.add("0");
                            newPlayer.add("0");
                            currentLongitude = -85.407286;
                            currentLatitude = 40.203264;
                            dailyScore = 0;
                            totalScore = 0;

                            playersList.add(newPlayer);
                            indexOfPlayer = playersList.indexOf(newPlayer);
                        }

                    }
                    catch(FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    catch(IOException e) {
                        e.printStackTrace();
                    }
                }

            } // players list is not empty, so check it
            else {
                boolean foundPlayerProfile = false;
                // check player list for current username
                for(int i = 0; i < playersList.size(); i++)  {
                    if(currentUsername.equals(playersList.get(i).get(0))) {
                        currentKey = playersList.get(i).get(1);
                        currentLongitude = Double.parseDouble(playersList.get(i).get(2));
                        currentLatitude = Double.parseDouble(playersList.get(i).get(3));
                        dailyScore = Integer.parseInt(playersList.get(i).get(4));
                        totalScore = Integer.parseInt(playersList.get(i).get(5));
                        indexOfPlayer = i;
                        foundPlayerProfile = true;
                        showBeginOfDay = true;
                        break;
                    }
                } // if not found create new user profile
                if(!foundPlayerProfile) {
                    ArrayList<String> newPlayer = new ArrayList<String>();
                    newPlayer.add(currentUsername);
                    currentKey = UUID.randomUUID().toString();
                    newPlayer.add(currentKey);
                    newPlayer.add("-85.407286");
                    newPlayer.add("40.203264");
                    newPlayer.add("0");
                    newPlayer.add("0");
                    currentLongitude = -85.407286;
                    currentLatitude = 40.203264;
                    dailyScore = 0;
                    totalScore = 0;

                    playersList.add(newPlayer);
                    indexOfPlayer = playersList.indexOf(newPlayer);
                }
            }
            socket.emit("login", currentUsername, currentKey, dailyScore, totalScore, scoreMultiplier);
        }

    }

    private void toggleLoginButton(Boolean enableDisable) {
        int toggleButton;
        //Get login button from UI
        Button login = findViewById(R.id.login_button);
        if (enableDisable) {
            //If the player is logging in set button Invisible
            toggleButton = View.INVISIBLE;
        } else {
            //If the player has logged out set button visible
            toggleButton = View.VISIBLE;
        }
        login.setVisibility(toggleButton);
    }

    public void toggleHideUserNameInput(Boolean toggle){
        TextView usernameTextBox = findViewById(R.id.userName_text_input);
        usernameTextBox.setEnabled(toggle);
    }

    private void saveUserProfiles() {
        Log.d("PLISTSAVE", "save profiles called.");
        File file = new File(getApplicationContext().getFilesDir(), "player_profiles");

        if(file.exists()) {
            try {
                FileOutputStream outputStream = openFileOutput("player_profiles", Context.MODE_PRIVATE);

                String output = "";

                for(int i = 0; i < playersList.size(); i++) {
                    for(int k = 0; k < playersList.get(0).size(); k++) {
                        output += playersList.get(i).get(k) + ",";
                    }
                    output = output.substring(0, output.length() - 1);
                    output += ";";
                }
                Log.d("PLISTSAVE", output);
                outputStream.write(output.getBytes());
                outputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    ///////////////////////////////////////////////////

    //Logout

    public void logout(View view) {
        if (mapInFocus) {
            socket.emit("logoff");
            saveUserProfiles();
            switchToLoginScreen(view);
            toggleLoginButton(false);
            //Remove everything from style
            removeNonWeatherFromStyle();
            //Reset Selection (Basically entire app selection booleans)
            resetConditions();
        } else {
            //When logging out from end of day screen
            socket.emit("logoff");
            removeEndOfDayScreen();
            addLoginScreen();
        }
    }

    //////////////////////////////////////////////////

    //Stop Travel Methods

    public void stopTravel(View view) {
        //If in focus (No Other screens on top)
        if (mapInFocus) {
            //When the stop travel button ic clicked, display in put confirmation screen
            // LayoutInflater inflater = getLayoutInflater();
           // inputConfirmationScreen = inflater.inflate(R.layout.input_confirmation, null);
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
            //If the player isn't traveling set button to invisible and label to visible
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
        //If the player selects yes on the input confirmation screen emit stop travel
        socket.emit("stopTravel");
        //Remove current route from screen
        removeRoute();
        //Set time left to 0
        updateTimeLeft(0);
        //Mark that the player doesn't have a set route now
        isTraveling = false;
        //Remove input confirmation screen
        removeDestinationMarker();
        removeInputConfirmationScreen();
    }

    ////////////////////////////////////////////////////

    //Screen Methods

    private void removeEndOfDayScreen() {
        if (!endOfDayScreen.hasFocus()) {
            ((ViewGroup) endOfDayScreen.getParent()).removeView(endOfDayScreen);
        }
    }

    public void switchToEndOfDayScreen() {
        mapInFocus = false;
        if(!endOfDayScreen.hasFocus()) {
            // LayoutInflater inflater = getLayoutInflater();
            // endOfDayScreen = inflater.inflate(R.layout.end_of_day_screen, null);
            getWindow().addContentView(endOfDayScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void removeInputConfirmationScreen(){
        ((ViewGroup) inputConfirmationScreen.getParent()).removeView(inputConfirmationScreen);
    }

    public void switchToHowToPlayScreen(View view) {
        if (mapInFocus) {
            mapInFocus = false;
            if(!howToPlayScreen.hasFocus()) {
                // LayoutInflater inflater = getLayoutInflater();
                // howToPlayScreen = inflater.inflate(R.layout.how_to_play, null);
                getWindow().addContentView(howToPlayScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
    }

    public void removeHowToPlayScreen(View view){
        mapInFocus = true;
        ((ViewGroup) howToPlayScreen.getParent()).removeView(howToPlayScreen);
    }

//    public void switchToMainScreen(View view) {
//        removeLoginScreen();
//    }

    public void switchToLoginScreen(View view) {
        if (mapInFocus) {
            addLoginScreen();
        }
    }

    private void removeLoginScreen() {
        mapInFocus = true;
        ((ViewGroup) loginScreen.getParent()).removeView(loginScreen);
    }

    private void addLoginScreen() {
        mapInFocus = false;
        if(!loginScreen.hasFocus()) {
            getWindow().addContentView(loginScreen, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /////////////////////////////////////////////////

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        appInFocus = true;
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        appInFocus = false;
        saveUserProfiles();
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
