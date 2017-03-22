package com.shiverwind.routemap;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivityFragment extends Fragment implements OnMapReadyCallback {

    private MapView mMapView;
    private GoogleMap gMap;
    private LatLng originPoint;
    private List<MarkerOptions> markers = new ArrayList<>();

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_maps, container, false);

        mMapView = (MapView) rootView.findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();
        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMapView.getMapAsync(this);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getActivity().getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
                .setTypeFilter(AutocompleteFilter.TYPE_FILTER_ESTABLISHMENT)
                .build();
        autocompleteFragment.setFilter(typeFilter);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                gMap.clear();
                markers.clear();

                MarkerOptions origin = new MarkerOptions().position(originPoint).title("Starting Position");
                markers.add(origin);
                gMap.addMarker(origin);

                LatLng destination = place.getLatLng();
                MarkerOptions dest = new MarkerOptions().position(destination).title(place.getName().toString());
                markers.add(dest);
                gMap.addMarker(dest);

                new JsonRequest().execute("https://maps.googleapis.com/maps/api/directions/json?origin="+originPoint.latitude+","+originPoint.longitude+"&destination="+destination.latitude+","+destination.longitude+"&key=AIzaSyBHhWt30FFbFAg0zIuqmdUsx1ele2uwIP4");
            }

            @Override
            public void onError(Status status) {
                Log.e("placeError", "onError: Status = " + status.toString());

                Toast.makeText(getActivity(), "Place selection failed: " + status.getStatusMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    private class JsonRequest extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            System.out.println(params[0]);
            return getJSON(params[0], 5000);
        }

        protected void onPostExecute(String encodedPolyline) {
            ParserTask parserTask = new ParserTask();

            parserTask.execute(encodedPolyline);
        }

    }

    public String getJSON(String url, int timeout) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.connect();
            int status = c.getResponseCode();

            switch (status) {
                case 200:
                case 201:
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    return sb.toString();
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return null;
    }

    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>> >{

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try{
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                // Starts parsing data
                routes = parser.parse(jObject);
            }catch(Exception e){
                e.printStackTrace();
            }
            return routes;
        }

        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            // Traversing through all the routes
            for(int i=0;i<result.size();i++){
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();

                // Fetching i-th route
                List<HashMap<String, String>> path = result.get(i);

                // Fetching all the points in i-th route
                for(int j=0;j<path.size();j++){
                    HashMap<String,String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(10);
                lineOptions.color(Color.RED);
            }

            // Drawing polyline in the Google Map for the i-th route
            gMap.addPolyline(lineOptions);

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (MarkerOptions marker : markers ) {
                builder.include(marker.getPosition());
            }
            LatLngBounds bounds = builder.build();

            int padding = 100; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            gMap.animateCamera(cu);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;

        LatLng defaultPosition = new LatLng(3.5972208, 98.6628681);
        gMap.moveCamera(CameraUpdateFactory.newLatLng(defaultPosition));
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPosition, googleMap.getMaxZoomLevel() - 5));

        if (ActivityCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        } else {
            GPSTracker gps = new GPSTracker(getActivity());
            if (gps.canGetLocation()) {
                originPoint = new LatLng(gps.getLatitude(), gps.getLongitude());
                gMap.addMarker(new MarkerOptions().position(originPoint).title("Starting Position"));
                gMap.moveCamera(CameraUpdateFactory.newLatLng(originPoint));
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(originPoint, googleMap.getMaxZoomLevel() - 5));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    GPSTracker gps = new GPSTracker(getActivity());
                    if(gps.canGetLocation()) {
                        originPoint = new LatLng(gps.getLatitude(), gps.getLongitude());
                        gMap.addMarker(new MarkerOptions().position(originPoint).title("Starting Position"));
                        gMap.moveCamera(CameraUpdateFactory.newLatLng(originPoint));
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(originPoint, gMap.getMaxZoomLevel() - 5));
                    }
                } else {
                    // permission denied
                }
                return;
            }
        }
    }

}
