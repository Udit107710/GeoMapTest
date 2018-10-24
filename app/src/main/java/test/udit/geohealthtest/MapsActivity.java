package test.udit.geohealthtest;

import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import test.udit.geohealthtest.R;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int DEFAULT_ZOOM = 5;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final String TAG = "MainActivity";
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private static final LatLng mDefaultLocation = new LatLng(28.7041, 77.1025);

    private GoogleMap mMap;
    private TextView textView;
    private EditText mapInput;
    private Button mapButton;
    private CameraPosition mCameraPosition;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private Location mLastKnownLocation;
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference mrefernence;
    private ArrayList<LatLng> mList = new ArrayList<LatLng>();
    private SupportMapFragment mapFragment;
    private Geocoder geocoder;

    private boolean mLocationPermissionGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        getLocationPermission();

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this);

        textView = (TextView) findViewById(R.id.textView_map);
        mapInput = (EditText) findViewById(R.id.map_input);
        mapButton = (Button) findViewById(R.id.map_button);

        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mapInput.getText().toString().isEmpty())
                    Toast.makeText(getApplicationContext(), "Enter string to search!",Toast.LENGTH_LONG).show();
                else {
                    String search = mapInput.getText().toString();
                    try {
                        List<Address> addressList = geocoder.getFromLocationName(search,1);
                        Address address = addressList.get(0);
                        LatLng placeLatLang = new LatLng(address.getLatitude(), address.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(placeLatLang, 13));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        mrefernence = database.getReference("Coordinates");

        mrefernence.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mList.clear();
                for(DataSnapshot dataSnapshot1 : dataSnapshot.getChildren())
                {
                    MyLocation myLocation = dataSnapshot1.getValue(MyLocation.class);
                    //Log.d(TAG,   myLocation.getLatitude() + " " + myLocation.getLongitude());
                    mList.add(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
                }
                mapFragment.getMapAsync(MapsActivity.this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        updateLocationUI();
        getDeviceLocation();

        mProvider = new HeatmapTileProvider.Builder().data(mList).build();
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));

        mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                calculateDensity();
            }
        });

    }

    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                 mFusedLocationProviderClient.getLastLocation().addOnCompleteListener(this, new OnCompleteListener<Location>(){
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        }
        catch (SecurityException e)
        {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }

        updateLocationUI();
    }

    public void calculateDensity()
    {
        int counter = 0;

        LatLngBounds latLngBounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        /*

        LatLng nearRight = mMap.getProjection().getVisibleRegion().nearRight;
        LatLng nearLeft = mMap.getProjection().getVisibleRegion().nearLeft;
        LatLng farRight = mMap.getProjection().getVisibleRegion().farRight;
        LatLng farLeft = mMap.getProjection().getVisibleRegion().farLeft;


        Double minLat,maxLat,minLong,maxLong;

        minLong = (nearLeft.longitude < farRight.longitude) ? nearLeft.longitude : farRight.longitude ;
        maxLong = (nearLeft.longitude < farRight.longitude) ? farRight.longitude : nearLeft.longitude ;

        maxLat = (farRight.latitude < nearRight.latitude) ?  nearRight.latitude : farRight.latitude ;
        minLat = (farRight.latitude < nearRight.latitude) ?  farRight.latitude :  nearRight.latitude ;

        ArrayList<LatLng> newList = new ArrayList<LatLng>();
        for(int i =0; i < mList.size() ; ++i)
        {
            LatLng latLng = mList.get(i);
            if(minLat < latLng.latitude && latLng.latitude < maxLat && minLong < latLng.longitude && latLng.longitude < maxLong)
         {
             newList.add(latLng);
             counter++;
         }
        }
//
//
//        for(int i =0; i < mList.size(); ++i)
//        {
//            LatLng latLng = mList.get(i);
//            if(
//                    checkCoordiants(latLng.latitude,latLng.longitude,farLeft.latitude,farLeft.longitude,farRight.latitude,farRight.longitude) &&
//                    checkCoordiants(latLng.latitude,latLng.longitude,nearLeft.latitude,nearLeft.longitude,farLeft.latitude,farLeft.longitude) &&
//                    checkCoordiants(latLng.latitude,latLng.longitude,farRight.latitude,farRight.latitude,nearRight.latitude,nearRight.longitude) &&
//                    checkCoordiants(latLng.latitude,latLng.longitude,nearRight.latitude,nearRight.longitude,nearLeft.latitude,nearLeft.longitude)
//              ) counter ++;
//        }

        */


        for(int i =0;i < mList.size();++i) if(latLngBounds.contains(mList.get(i))) counter++;
        textView.setText(counter + " People in the area");


    }

    private boolean checkCoordiants(double x,double y,double x1,double y1,double x2,double y2)
    {
        if ( y > (y2-y1)/(x2-x1)*(x-x1)+y1 ) return true;
        else return false;
    }
}