package com.example.test.googlemap;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {


    private GoogleApiClient mGoogleApiClient = null;
    private GoogleMap mGoogleMap = null;
    private Marker currentMarker = null;

    private static final String TAG = "googlemap_example";
    private static final int GPS_ENABLE_REQUEST_CODE = 2001; // 다이얼로그와 인텐트를 주고받기 위한 코드
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2002; // 위치 권한 획득 코드
    private static final int UPDATE_INTERVAL_MS = 15000;  // 15초 위치를 얻어오는데 걸린 시간
    private static final int FASTEST_UPDATE_INTERVAL_MS = 15000; // 15초 위치를 얻고나서 업데이트되는 시간

    private AppCompatActivity mActivity;
    boolean askPermissionOnceAgain = false;     // onResume에서 앱의 위치사용권한 설정 여부 값
    boolean mRequestingLocationUpdates = false; // 사용자 위치 업데이트 요청 여부 값
    boolean mMoveMapByUser = true;              // 유저가 지정한 위치사용 여부 값 사용 여부 값
    boolean mMoveMapByAPI = true;               // api상의 기본 위치 값 사용 여부 값
    LatLng currentPosition = null;
    Marker addedMarker = null;

    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference();

    private String CHAT_NAME; // 채팅방 이름
    private String USER_NAME; // 유저 이름
    private ListView chat_view;
    private EditText chat_edit;
    private Button chat_send;
    List<ChatDTO> searchUser = new ArrayList<>(); //유저 위치 객체를 담는 리스트
    List<Destination> searchDestination = new ArrayList<>(); // 목적지 위치 객체를 담는 리스트

    LocationRequest locationRequest = new LocationRequest()  // 위치요청 및 업데이트주기 설정 객체
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY) // 배터리 사용고려 x 정확도 최우선
            .setInterval(UPDATE_INTERVAL_MS)
            .setFastestInterval(FASTEST_UPDATE_INTERVAL_MS);

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { // 메뉴바 사용
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu,menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) { // 메뉴바 아이템 선택시 이벤트
        switch(item.getItemId()){
            case R.id.menu1:
                ChatDTO chat = new ChatDTO(USER_NAME, USER_NAME+"님이 채팅방을 나가셨습니다.");
                databaseReference.child("chat").child(CHAT_NAME).push().setValue(chat); // 데이터 푸쉬
                databaseReference.child("location").child(CHAT_NAME+"Location").child(USER_NAME).removeValue();
                finish();

                return true;
            case R.id.menu2:
                final List<String> ListItems = new ArrayList<>();
                int i;
                for(i =0;i<searchUser.size();i++) {
                    ListItems.add(searchUser.get(i).getUserName()+"님의 위치보기");
                }
                final CharSequence[] items =  ListItems.toArray(new String[ ListItems.size()]);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("유저 위치검색");
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int pos) {
                        String selectedText = items[pos].toString();
                        Toast.makeText(MainActivity.this, selectedText, Toast.LENGTH_SHORT).show();
                        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(searchUser.get(pos).getLatitude(), searchUser.get(pos).getLongitude()));
                        mGoogleMap.moveCamera(cameraUpdate);
                    }
                });
                builder.show();
                return true;
            case R.id.menu3:
                if(searchDestination.size()!=0) {
                    DistanceMeasurement();
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(searchDestination.get(0).getLatitude(), searchDestination.get(0).getLongitude()));
                    mGoogleMap.moveCamera(cameraUpdate);

                }
                else
                    Toast.makeText(MainActivity.this, "목적지가 설정되어있지 않습니다.", Toast.LENGTH_SHORT).show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate 실행. ");

        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate");
        mActivity = this;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();


        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        chat_view = (ListView) findViewById(R.id.chat_view); // 여기부터 채팅
        chat_edit = (EditText) findViewById(R.id.chat_edit);
        chat_send = (Button) findViewById(R.id.chat_sent);

        // 로그인 화면에서 받아온 채팅방 이름, 유저 이름 저장
        Intent intent = getIntent();
        CHAT_NAME = intent.getStringExtra("chatName");
        USER_NAME = intent.getStringExtra("userName");

        // 채팅 방 입장
        openChat(CHAT_NAME);

        // 메시지 전송 버튼에 대한 클릭 리스너 지정
        chat_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (chat_edit.getText().toString().equals(""))
                    return;

                ChatDTO chat = new ChatDTO(USER_NAME, chat_edit.getText().toString()); //ChatDTO를 이용하여 데이터를 묶는다.

                Log.d(TAG, "chat 객체가 생성되었습니다. ");
                databaseReference.child("chat").child(CHAT_NAME).push().setValue(chat); // 데이터 푸쉬
                chat_edit.setText(""); //입력창 초기화
            }
        });
}

    @Override
    public void onResume() {

        super.onResume();

        if (mGoogleApiClient.isConnected()) {

            Log.d(TAG, "onResume : call startLocationUpdates");
            if (!mRequestingLocationUpdates) startLocationUpdates();
        }


        //앱 정보에서 퍼미션을 허가했는지를 다시 검사해봐야 한다.
        if (askPermissionOnceAgain) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                askPermissionOnceAgain = false;

                checkPermissions();
            }
        }
    }


    private void startLocationUpdates() { // 위치 값을 불러올수있게 설정

        if (!checkLocationServicesStatus()) {

            Log.d(TAG, "startLocationUpdates : call showDialogForLocationServiceSetting");
            showDialogForLocationServiceSetting(); // GPS 활성화 다이얼로그를 보여주는 팝업창(메소드) 생성
        }else {
            // 위치권한이 허용되지 않았다면
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "startLocationUpdates : 퍼미션 안가지고 있음");
                return;
            }

            // 위치권한이 허용되었다면 mRequestingLocationUpdates를 true로 바꾸고 시작 locationRequest는 위치값 수신 옵션
            Log.d(TAG, "startLocationUpdates : call FusedLocationApi.requestLocationUpdates 위치 업데이트 시작");
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
            mRequestingLocationUpdates = true;

            mGoogleMap.setMyLocationEnabled(true);

        }

    }



    private void stopLocationUpdates() { // onStop() 실행 시 업데이트 중단 메소드

        Log.d(TAG,"stopLocationUpdates : LocationServices.FusedLocationApi.removeLocationUpdates");
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mRequestingLocationUpdates = false;
    }



    @Override
    public void onMapReady(GoogleMap googleMap) {


        Log.d(TAG, "onMapReady : 온맵레디");

        mGoogleMap = googleMap;


        //런타임 퍼미션 요청 대화상자나 GPS 활성 요청 대화상자 보이기전에
        //지도의 초기위치를 서울로 이동
        setDefaultLocation();

        mGoogleMap.getUiSettings().setZoomControlsEnabled(false);
        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
        mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(15));
        mGoogleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener(){

            @Override
            public boolean onMyLocationButtonClick() {

                Log.d( TAG, "onMyLocationButtonClick : 위치에 따른 카메라 이동 활성화");
                mMoveMapByAPI = true;
                return true;
            }
        });
        mGoogleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng latLng) {

                Log.d( TAG, "onMapClick :");
            }
        });

        mGoogleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {

            @Override
            public void onCameraMoveStarted(int i) {

                if (mMoveMapByUser == true && mRequestingLocationUpdates){

                    Log.d(TAG, "onCameraMove : 위치에 따른 카메라 이동 비활성화");
                    mMoveMapByAPI = false;
                }

                mMoveMapByUser = true;

            }
        });

        mGoogleMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {

            @Override
            public void onCameraMove() {


            }
        });

        mGoogleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener(){

            @Override
            public void onMapLongClick(final LatLng latLng) {

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.dialog_place_info, null);
                builder.setView(view);
                final Button button_submit = (Button) view.findViewById(R.id.button_dialog_placeInfo);
                final EditText editText_placeTitle = (EditText) view.findViewById(R.id.editText_dialog_placeTitle);
                final EditText editText_placeDesc = (EditText) view.findViewById(R.id.editText_dialog_placeDesc);

                final AlertDialog dialog = builder.create();
                button_submit.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String string_placeTitle = editText_placeTitle.getText().toString();
                        String string_placeDesc = editText_placeDesc.getText().toString();
                        Toast.makeText(MainActivity.this, string_placeTitle+"\n"+string_placeDesc,Toast.LENGTH_SHORT).show();

                        Destination destination = new Destination(latLng.latitude,latLng.longitude,string_placeDesc,string_placeTitle);

                        databaseReference.child("destination").child(CHAT_NAME+"destination").child("desti").setValue(destination);
                        searchDestination.removeAll(searchDestination);
                        FirebaseDatabase.getInstance().getReference("destination").child(CHAT_NAME+"destination").addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                                Destination destination = dataSnapshot.getValue(Destination.class);
                                //맵을 클릭시 현재 위치에 마커 추가

                                MarkerOptions markerOptions = new MarkerOptions();
                                markerOptions.position(new LatLng(destination.getLatitude(),destination.getLongitude()));

                                markerOptions.title(destination.getString_placeTitle());
                                markerOptions.snippet(destination.getString_placeDesc());
                                BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(R.drawable.destination);
                                Bitmap b=bitmapdraw.getBitmap();
                                Bitmap smallMarker = Bitmap.createScaledBitmap(b, 70, 70, false);
                                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));

                                if ( addedMarker != null ) addedMarker.remove();
                                addedMarker = mGoogleMap.addMarker(markerOptions);

                                dialog.dismiss();
                            }

                            @Override
                            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                            }
                            @Override
                            public void onChildRemoved(DataSnapshot dataSnapshot) {

                            }

                            @Override
                            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });


                    }
                });

                dialog.show();

            }
        });
    }


    @Override

    public void onLocationChanged(Location location) {
        currentPosition
                = new LatLng( location.getLatitude(), location.getLongitude());
        Log.d(TAG, "onLocationChanged : 위치가 바뀌었습니다 ");

        final ChatDTO chatDTO = new ChatDTO(USER_NAME,"",location.getLatitude(),location.getLongitude());
        //databaseReference.child("chat").child(CHAT_NAME).push().setValue(chatDTO);
        databaseReference.child("location").child(CHAT_NAME+"Location").child(USER_NAME).setValue(chatDTO);
        mGoogleMap.clear();
        searchDestination.removeAll(searchDestination);
        FirebaseDatabase.getInstance().getReference("destination").child(CHAT_NAME+"destination").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                Destination destination = dataSnapshot.getValue(Destination.class);
                //맵을 클릭시 현재 위치에 마커 추가
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(new LatLng(destination.getLatitude(),destination.getLongitude()));
                searchDestination.add(destination);
                markerOptions.title(destination.getString_placeTitle());
                markerOptions.snippet(destination.getString_placeDesc());
                BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(R.drawable.destination);
                Bitmap b=bitmapdraw.getBitmap();
                Bitmap smallMarker = Bitmap.createScaledBitmap(b, 70, 70, false);
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));

                if ( addedMarker != null ) addedMarker.remove();
                addedMarker = mGoogleMap.addMarker(markerOptions);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        searchUser.removeAll(searchUser);
        FirebaseDatabase.getInstance().getReference("location").child(CHAT_NAME+"Location").addChildEventListener(new ChildEventListener() {
            @Override

            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                ChatDTO chatDTO = dataSnapshot.getValue(ChatDTO.class);

                    MarkerOptions makerOptions = new MarkerOptions();
                    makerOptions.position(new LatLng(chatDTO.getLatitude(), chatDTO.getLongitude())).title(chatDTO.getUserName()); // 타이틀.
                    BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(R.drawable.user);
                    Bitmap b=bitmapdraw.getBitmap();
                    Bitmap smallMarker = Bitmap.createScaledBitmap(b, 90, 90, false);
                    makerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));
                    mGoogleMap.addMarker(makerOptions);

                    searchUser.add(chatDTO);
                    Log.d(TAG, "searchUser에 정상적으로 저장");

                //CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(chatDTO.getLatitude(), chatDTO.getLongitude()));
                //mGoogleMap.moveCamera(cameraUpdate);

                }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {


            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        currentPosition
                = new LatLng( location.getLatitude(), location.getLongitude());
        setCurrentLocation(location);

    }


    @Override
    protected void onStart() {

        if(mGoogleApiClient != null && mGoogleApiClient.isConnected() == false){

            Log.d(TAG, "onStart: mGoogleApiClient connect");
            mGoogleApiClient.connect();
        }

        super.onStart();
    }

    @Override
    protected void onStop() { // 화면 전환 시 위치 업데이트 중단 및 연결 해제

        if (mRequestingLocationUpdates) {

            Log.d(TAG, "onStop : call stopLocationUpdates");
            stopLocationUpdates();
        }

        if ( mGoogleApiClient.isConnected()) {

            Log.d(TAG, "onStop : mGoogleApiClient disconnect");
            mGoogleApiClient.disconnect();
        }

        super.onStop();
    }


    @Override
    public void onConnected(Bundle connectionHint) {


        if ( mRequestingLocationUpdates == false ) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                int hasFineLocationPermission = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

                if (hasFineLocationPermission == PackageManager.PERMISSION_DENIED) {

                    ActivityCompat.requestPermissions(mActivity,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION); // 위치 권한 요청

                } else {

                    Log.d(TAG, "onConnected : 퍼미션 가지고 있음");
                    Log.d(TAG, "onConnected : call startLocationUpdates");
                    startLocationUpdates();
                    mGoogleMap.setMyLocationEnabled(true);
                }

            }else{

                Log.d(TAG, "onConnected : call startLocationUpdates");
                startLocationUpdates();
                mGoogleMap.setMyLocationEnabled(true);
            }
        }
    }


    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) { // 위치 권한 획득 실패 시 디폴트 위치로 이동

        Log.d(TAG, "onConnectionFailed");
        setDefaultLocation();
    }


    @Override
    public void onConnectionSuspended(int cause) {
        /*
        Log.d(TAG, "onConnectionSuspended");
        if (cause == CAUSE_NETWORK_LOST)
            Log.e(TAG, "onConnectionSuspended(): Google Play services " +
                    "connection lost.  Cause: network lost.");
        else if (cause == CAUSE_SERVICE_DISCONNECTED)
            Log.e(TAG, "onConnectionSuspended():  Google Play services " +
                    "connection lost.  Cause: service disconnected");*/
    }

    public void DistanceMeasurement() { // 목적지까지 남은 거리 측정
        if(currentPosition!=null) {
            double distance = SphericalUtil.computeDistanceBetween(currentPosition, addedMarker.getPosition());

            Toast.makeText(this, searchDestination.get(0).getString_placeTitle() + "까지" + (int) distance + "m 남음", Toast.LENGTH_LONG).show();
        }

    }

    public boolean checkLocationServicesStatus() { // GPS나 기지국으로 부터 위치 확인 가능 여부 리턴
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }


    public void setCurrentLocation(Location location) { // 현재 위치로 카메라 줌

        mMoveMapByUser = false;
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(currentLatLng);
        mGoogleMap.moveCamera(cameraUpdate);
    }


    public void setDefaultLocation() { // 맵의 디폴트 위치 값 설정

        mMoveMapByUser = false;

        //디폴트 위치, Seoul
        LatLng DEFAULT_LOCATION = new LatLng(37.56, 126.97);
        String markerTitle = "위치정보 가져올 수 없음";
        String markerSnippet = "위치 퍼미션과 GPS 활성 요부 확인하세요";

        if (currentMarker != null) currentMarker.remove();

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(DEFAULT_LOCATION);
        markerOptions.title(markerTitle);
        markerOptions.snippet(markerSnippet);
        markerOptions.draggable(true);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        currentMarker = mGoogleMap.addMarker(markerOptions);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15);
        mGoogleMap.moveCamera(cameraUpdate);

    }

    //여기부터는 런타임 퍼미션 처리을 위한 메소드들
    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermissions() {
        boolean fineLocationRationale = ActivityCompat
                .shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);
        int hasFineLocationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION); // 위치 권한 확인

        if (hasFineLocationPermission == PackageManager
                .PERMISSION_DENIED && fineLocationRationale)
            showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");

        else if (hasFineLocationPermission
                == PackageManager.PERMISSION_DENIED && !fineLocationRationale) {
            showDialogForPermissionSetting("퍼미션 거부 + Don't ask again(다시 묻지 않음) " +
                    "체크 박스를 설정한 경우로 설정에서 퍼미션 허가해야합니다.");
        } else if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED) {


            Log.d(TAG, "checkPermissions : 퍼미션 가지고 있음");

            if ( mGoogleApiClient.isConnected() == false) {

                Log.d(TAG, "checkPermissions : 퍼미션 가지고 있음");
                mGoogleApiClient.connect();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) { // permisssionRequest 함수 실행 시 요청 결과를 가져오는 메소드

        if (permsRequestCode
                == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION && grantResults.length > 0) {

            boolean permissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (permissionAccepted) {


                if ( mGoogleApiClient.isConnected() == false) {

                    Log.d(TAG, "onRequestPermissionsResult : mGoogleApiClient connect");
                    mGoogleApiClient.connect();
                }



            } else {

                checkPermissions();
            }
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ActivityCompat.requestPermissions(mActivity,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        });

        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });
        builder.create().show();
    }

    private void showDialogForPermissionSetting(String msg) { // 퍼미션 허용 다이얼로그 창에서 다시 묻지 않음을 선택 시 다음에 앱 실행 시 나오는 다이얼로그

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(true);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

                askPermissionOnceAgain = true;

                Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + mActivity.getPackageName()));
                myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
                myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivity.startActivity(myAppSettings);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });
        builder.create().show();
    }


    //여기부터는 GPS 활성화를 위한 메소드들
    private void showDialogForLocationServiceSetting() { // GSP 비활성화 시 실행되는 메소드

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 비활성화");
        builder.setMessage("앱을 사용하기 위해서는 위치 서비스가 필요합니다.\n"
                + "위치 설정을 수정하실래요?");
        builder.setCancelable(true);
        builder.setPositiveButton("설정", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPSSettingIntent
                        = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) { // GPS활성화 요청시 결과 값을 가져오는 메소드
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            case GPS_ENABLE_REQUEST_CODE:

                //사용자가 GPS 활성 시켰는지 검사
                if (checkLocationServicesStatus()) {
                    if (checkLocationServicesStatus()) {

                        Log.d(TAG, "onActivityResult : 퍼미션 가지고 있음");


                        if ( mGoogleApiClient.isConnected() == false ) {

                            Log.d( TAG, "onActivityResult : mGoogleApiClient connect ");
                            mGoogleApiClient.connect();
                        }
                        return;
                    }
                }

                break;
        }
    }


//------------------------------------------------------------------------------------------------------------------------------------------------------------ 채팅
    private void addMessage(DataSnapshot dataSnapshot, ArrayAdapter<String> adapter) {
        ChatDTO chatDTO = dataSnapshot.getValue(ChatDTO.class);
        if(chatDTO.getMessage().length() == 0){
            return;
        }
        adapter.add(chatDTO.getUserName() + " : " + chatDTO.getMessage());
        chat_view.setSelection(adapter.getCount()-1);
    }


    private void openChat(String chatName) {
        // 리스트 어댑터 생성 및 세팅
        ChatDTO join = new ChatDTO(USER_NAME,USER_NAME+"님이 입장하셨습니다.");
        databaseReference.child("chat").child(chatName).push().setValue(join);
        final ArrayAdapter<String> adapter

                = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1);
        chat_view.setAdapter(adapter);

        // 데이터 받아오기 및 어댑터 데이터 추가 및 삭제 등..리스너 관리
        databaseReference.child("chat").child(chatName).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                addMessage(dataSnapshot, adapter);
                Log.e("LOG", "s:"+s);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

}
