package se.standersson.icingalert;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private String reply;
    private static JSONObject data;
    public static List<Host> hosts;
    FragmentPagerAdapter adapterViewPager;
    private int hostListCount;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("reply", reply);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                logOut();
                return true;
            case R.id.preferences:
                Intent prefIntent = new Intent(this, Preferences.class);
                startActivity(prefIntent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Replace the current Intent with this one
        setIntent(intent);
        refresh();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //Toolbar mainToolbar = (Toolbar) findViewById(R.id.main_toolbar);
       // setActionBar(mainToolbar);

        // Subscribe to notifications according to saved settings
        SharedPreferences notificationPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (notificationPrefs.getBoolean("host_push", false)){
            FirebaseMessaging.getInstance().subscribeToTopic("hosts");
        }
        if (notificationPrefs.getBoolean("service_push", false)){
            FirebaseMessaging.getInstance().subscribeToTopic("services");
        }


        /*
         * Set up a callback for refresh PullDown
         */
        /*swipeContainer = (SwipeRefreshLayout) findViewById(R.id.exp_swipe);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() { refresh();}
        });*/

        Intent intent = getIntent();


        // If we have a saved state, use that to create the list, otherwise, get from the intent
        if (savedInstanceState != null) {
            reply = savedInstanceState.getString("reply");
        } else {
            reply = intent.getStringExtra("reply");
        }

        try {
            data = new JSONObject(reply);
        } catch (JSONException e) {
            Toast.makeText(this, "Unable to parse JSON", Toast.LENGTH_LONG).show();
            logOut();
        }

        //Create expandableListView and fill with data
        createExpandableListSummary();

        ViewPager mainViewPager = (ViewPager) findViewById(R.id.main_view_pager);
        adapterViewPager = new MainPagerAdapter(getSupportFragmentManager(), hostListCount);
        mainViewPager.setAdapter(adapterViewPager);
    }

    private void createExpandableListSummary() {
        /*
         * Prepare status info into
         */
        hosts = new ArrayList<>();
        HashMap<String, Integer> hostPositions = new HashMap<>();

        int servicesCount = 0, hostsDownCount = 0, hostsCount = 0;

        // Check how many Hosts and Services are having trouble
        try {
            servicesCount = data.getJSONArray("services").length();
            hostsCount = data.getJSONArray("hosts").length();
            hostsDownCount = data.getJSONObject("status").getInt("num_hosts_down");
            hostListCount = hostsDownCount;
        } catch (JSONException e) {
            Toast.makeText(this, "Couldn't find a Host/Services Array", Toast.LENGTH_LONG).show();
            logOut();
        }

        try {
            String hostName, serviceName;
            String serviceDetails;
            int state;


            /*
            * Add all downed hosts to the list first in order to sort them to the top
             */
            for (int x = 0, y = 0; x < hostsCount; x++) {
                if (data.getJSONArray("hosts").getJSONObject(x).getJSONObject("attrs").getInt("state") == 1) {
                    hostName = data.getJSONArray("hosts").getJSONObject(x).getJSONObject("attrs").getString("name");
                    hostPositions.put(hostName, hosts.size());
                    hosts.add(new Host(hostName, x));
                    y++;
                    if (y == hostsDownCount) {
                        break;
                    }
                }
            }

            for (int x=0 ; x < servicesCount ; x++) {
                hostName = data.getJSONArray("services").getJSONObject(x).getJSONObject("attrs").getString("host_name");
                state = data.getJSONArray("services").getJSONObject(x).getJSONObject("attrs").getInt("state");
                if (state != 0 && !hostPositions.containsKey(hostName)){
                    hostPositions.put(hostName, hosts.size());
                    hosts.add(new Host(hostName));
                    hostListCount++;
                }
            }

            /*
            * Loop through all services and store the hostname and the location of their respective failing services
             */
            for (int x=0 ; x < servicesCount ; x++) {
                hostName = data.getJSONArray("services").getJSONObject(x).getJSONObject("attrs").getString("host_name");
                serviceName = data.getJSONArray("services").getJSONObject(x).getJSONObject("attrs").getString("name");
                serviceDetails = data.getJSONArray("services").getJSONObject(x).getJSONObject("attrs").getJSONObject("last_check_result").getString("output");
                state = data.getJSONArray("services").getJSONObject(x).getJSONObject("attrs").getInt("state");

                if (hostPositions.containsKey(hostName)) {
                    hosts.get(hostPositions.get(hostName)).addService(x, serviceName, serviceDetails, state);
                } else {
                    hostPositions.put(hostName, hosts.size());
                    hosts.add(new Host(hostName));
                    hosts.get(hostPositions.get(hostName)).addService(x, serviceName, serviceDetails, state);
                }
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Problem with parsing hosts/services", Toast.LENGTH_LONG).show();
        }
    }

    private void logOut () {
        // Clear credentials and go back to login page
        SharedPreferences prefStorage = getSharedPreferences("Login", 0);
        prefStorage.edit().putString("serverString", "").apply();
        prefStorage.edit().putString("username", "").apply();
        prefStorage.edit().putString("password", "").apply();
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void refresh() {

        //Get login credentials and make a call to get status data
        String[] prefsString = new String[3];
        SharedPreferences prefStorage = getSharedPreferences("Login", 0);
        prefsString[0] = prefStorage.getString("serverString", "");
        prefsString[1] = prefStorage.getString("username", "");
        prefsString[2] = prefStorage.getString("password", "");

        if (ServerInteraction.isConnected(getApplicationContext())){
            new refreshFetch().execute(prefsString);
        } else {
            Toast.makeText(getApplicationContext(), "No Network Connectivity", Toast.LENGTH_LONG).show();
        }

        ((MainPagerAdapter)adapterViewPager).getFragment(0).stopRefreshSpinner(true);
        //swipeContainer.setRefreshing(false);
    }

    private class refreshFetch extends AsyncTask<String[], Integer, String> {
        /*
         * AsyncTask that does fetches the data outside of the UI thread and then resets the
         * expandableListView
         */
        @Override
        protected String doInBackground(String[]... data) {
            try {
                return ServerInteraction.fetchData(data[0]);
            }catch (SocketTimeoutException e) {
                return "Connection Timed Out";
            } catch (MalformedURLException e) {
                return "Invalid URL";
            } catch (UnknownHostException e) {
                return "Resolve Failed";
            } catch (FileNotFoundException e) {
                return "FileNotFoundException";
            } catch (Exception e) {
                Log.e("NetworkException", e.toString());
                return "Unknown Exception";
            }
        }

        protected void onPostExecute(String reply){
            if (ServerInteraction.checkReply(getApplicationContext(), reply)) {
                try {
                    data = new JSONObject(reply);
                    //ExpandableListView listView = (ExpandableListView) findViewById(R.id.main_expand_list);
                    //createExpandableListSummary();
                    //ExpandableListAdapter listAdapter = new mainExpandableListAdapter(getApplicationContext(), hosts);
                    //listView.setAdapter(listAdapter);
                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(), "Unable to parse JSON", Toast.LENGTH_LONG).show();
                    logOut();
                }
            }
            ((MainPagerAdapter)adapterViewPager).getFragment(0).stopRefreshSpinner(true);   //swipeContainer.setRefreshing(false);
        }

    }

    private static class MainPagerAdapter extends FragmentPagerAdapter {
	    private static int NUM_ITEMS = 2;
        private int hostsDownNr;
        private SparseArray<ProblemFragment> fragmentArray = new SparseArray<>();

        MainPagerAdapter(android.support.v4.app.FragmentManager fragmentManager, Integer hostsCount) {
            super(fragmentManager);
            this.hostsDownNr = hostsCount;
        }

        // Returns total number of pages
        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        // Returns the fragment to display for that page
        @Override
        public android.support.v4.app.Fragment getItem(int position) {
            switch (position) {
                case 0: // Fragment # 0 - This will show FirstFragment
                    ProblemFragment troubleFragment = ProblemFragment.newInstance(hostsDownNr);
                    fragmentArray.put(position, troubleFragment);
                    return troubleFragment;
                case 1: // Fragment # 0 - This will show FirstFragment different title
                    ProblemFragment allFragment = ProblemFragment.newInstance(hosts.size());
                    fragmentArray.put(position, allFragment);
                    return allFragment;
                default:
                    return null;
            }
        }

        // Returns the page title for the top indicator
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Trouble";
                case 1:
                    return "All";
                default:
                    return "Wrong";
            }
        }

        private ProblemFragment getFragment(int position){
            return fragmentArray.get(position);
        }
    }

}
