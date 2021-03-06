package com.r2starbase.apo11o.coeus;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Stack;

public class CoeusActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener
        , DeviceListFragment.DeviceListListener
        , DeviceDetailFragment.DeviceDetailListener {
    public final static String TAG = "CoeusActivity";
    final static String COEUS_PREF = "COEUS_PREF";
    final static String TAG_STORE = "TAG_STORE";
    private SharedPreferences sp;
    private Stack<String> currentTag = new Stack<>();
    private WifiP2pManager pManager;
    private WifiP2pManager.Channel pChannel;
    private WifiP2pBroadcastReceiver pReceiver;
    private IntentFilter pFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup layout
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Setup Wifi P2P
        setupWifiP2p();

        // Load previous state if any
        sp = getSharedPreferences(CoeusActivity.COEUS_PREF, MODE_PRIVATE);
        changeFragment(sp.getString(CoeusActivity.TAG_STORE, HomeFragment.TAG));
    }

    private void setupWifiP2p() {
        pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        pChannel = pManager.initialize(this, getMainLooper(), null);
        pReceiver = new WifiP2pBroadcastReceiver(pManager, pChannel, this);
        pFilter = new IntentFilter();
        pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        pFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        pFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        pFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        String tag = currentTag.empty() ? "" : currentTag.peek();
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (getFragmentManager().getBackStackEntryCount() > 0 && tag.equals(DeviceDetailFragment.TAG)) {
            getFragmentManager().popBackStack();
            if (!currentTag.empty()) {
                currentTag.pop();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        String tag;
        Class fragClass;
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_home:
                tag = HomeFragment.TAG;
                fragClass = HomeFragment.class;
                break;
            case R.id.nav_ping:
                tag = DeviceListFragment.TAG;
                fragClass = DeviceListFragment.class;
                break;
            default:
                tag = HomeFragment.TAG;
                fragClass = HomeFragment.class;
        }

        changeFragment(tag, fragClass);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void changeFragment(String tag) {
        Class fragClass;
        switch (tag) {
            case HomeFragment.TAG:
                fragClass = HomeFragment.class;
                break;
            case DeviceListFragment.TAG:
                fragClass = DeviceListFragment.class;
                break;
            default:
                fragClass = HomeFragment.class;
        }
        changeFragment(tag, fragClass);
    }

    private void changeFragment(String tag, Class fragClass) {
        Fragment frag;
        FragmentManager fManager = getFragmentManager();
        frag = fManager.findFragmentByTag(tag);
        if (frag == null) {
            try {
                frag = (Fragment) fragClass.newInstance();
                Log.d(CoeusActivity.TAG, tag + " Created");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        fManager.beginTransaction().replace(R.id.content_main, frag, tag)
                .addToBackStack(currentTag.empty() ? null : currentTag.peek()).commit();
        currentTag.push(tag);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sp = getSharedPreferences(CoeusActivity.COEUS_PREF, MODE_PRIVATE);
        changeFragment(sp.getString(CoeusActivity.TAG_STORE, HomeFragment.TAG));
        // Wifi P2P stuff
        registerReceiver(pReceiver, pFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = sp.edit();
        if (!currentTag.empty()) {
            editor.putString(CoeusActivity.TAG_STORE, currentTag.peek());
        }
        editor.apply();
        // Wifi P2P stuff
        unregisterReceiver(pReceiver);
    }

    @Override
    public void startDiscovery() {
        pManager.discoverPeers(pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                DeviceListFragment dlFrag = (DeviceListFragment) getFragmentManager()
                        .findFragmentByTag(DeviceListFragment.TAG);
                dlFrag.stopRefresh();
            }

            @Override
            public void onFailure(int reason) {

            }
        });
    }

    @Override
    public void showDeviceDetail(DeviceInfo di) {
        FragmentManager fManager = getFragmentManager();
        DeviceDetailFragment ddf = new DeviceDetailFragment();
        ddf.setupDevice(di);
        fManager.beginTransaction().replace(R.id.content_main,
                ddf, DeviceDetailFragment.TAG)
                .addToBackStack(currentTag.peek()).commit();
        currentTag.push(DeviceDetailFragment.TAG);
    }

    @Override
    public void connect(WifiP2pConfig config) {
        pManager.connect(pChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Connect failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        pManager.removeGroup(pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {

            }
        });
    }

    @Override
    public void onChannelDisconnect() {

    }

    @Override
    public void cancelDisconnect() {

    }
}
