/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2016  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.geopaparazzi.library.core.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.geopaparazzi.library.R;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.network.NetworkUtilities;
import eu.geopaparazzi.library.routing.osmbonuspack.GeoPoint;
import eu.geopaparazzi.library.routing.osmbonuspack.GraphHopperRoadManager;
import eu.geopaparazzi.library.routing.osmbonuspack.MapQuestRoadManager;
import eu.geopaparazzi.library.routing.osmbonuspack.OSRMRoadManager;
import eu.geopaparazzi.library.routing.osmbonuspack.Road;
import eu.geopaparazzi.library.routing.osmbonuspack.RoadManager;
import eu.geopaparazzi.library.util.GPDialogs;
import eu.geopaparazzi.library.util.LibraryConstants;
import eu.geopaparazzi.library.util.PositionUtilities;

/**
 * Activity that performs geocoding on a user entered location.
 *
 * @author Adam Stroud &#60;<a href="mailto:adam.stroud@gmail.com">adam.stroud@gmail.com</a>&#62;
 * @author Andrea Antonello (www.hydrologis.com) - geopaparazzi adaptions/additions.
 */
public class GeocodeActivity extends AppCompatActivity {
    private static final int MAX_ADDRESSES = 30;
    public static final String OSRM = "OSRM";
    public static final String MAPQUEST = "Mapquest";
    public static final String GRAPHHOPPER = "Graphhopper";

    private String noValidItemSelectedMsg = null;
    private ProgressDialog orsProgressDialog;
    private ListView mListView;
    private FloatingActionButton gotoButton;
    private FloatingActionButton routeToButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_geocode);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        noValidItemSelectedMsg = getString(R.string.no_valid_destination_selected);

        gotoButton = (FloatingActionButton) findViewById(R.id.gotoButton);
        routeToButton = (FloatingActionButton) findViewById(R.id.routeToButton);
        gotoButton.hide();
        routeToButton.hide();

        mListView = (ListView) findViewById(R.id.resultslist);
    }

    @Override
    protected void onPause() {
        GPDialogs.dismissProgressDialog(orsProgressDialog);
        super.onPause();
    }

    /**
     * Lookup action.
     *
     * @param view parent.
     */
    public void onLookupLocationClick(View view) {
        if (!checkNetwork()) {
            return;
        }
        // TODO add it back when the version permits it
        // if (Geocoder.isPresent())
        // {
        EditText addressText = (EditText) findViewById(R.id.enterLocationValue);


        try {
            List<Address> addressList = new Geocoder(this).getFromLocationName(addressText.getText().toString(), MAX_ADDRESSES);
            if (addressList.size() == 0) {
                GPDialogs.infoDialog(this, getString(R.string.couldnt_find_geocache_results), null);
                return;
            }

            List<AddressWrapper> addressWrapperList = new ArrayList<AddressWrapper>();

            for (Address address : addressList) {
                addressWrapperList.add(new AddressWrapper(address));
            }

            mListView.setAdapter(new ArrayAdapter<AddressWrapper>(this, R.layout.activity_geocode_row, addressWrapperList));
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    gotoButton.show();
                    routeToButton.show();
                }
            });

        } catch (IOException e) {
            GPLog.error(this, "Could not geocode address", e); //$NON-NLS-1$
            new AlertDialog.Builder(this).setMessage(R.string.geocodeErrorMessage).setTitle(R.string.geocodeErrorTitle)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
        }
        // }
    }


    /**
     * Go to action.
     *
     * @param view parent.
     */
    public void goTo(View view) {
        if (!checkNetwork()) {
            return;
        }

        Intent intent = getIntent();
        if (mListView.getCheckedItemPosition() != ListView.INVALID_POSITION) {
            AddressWrapper addressWrapper = (AddressWrapper) mListView.getItemAtPosition(mListView.getCheckedItemPosition());

            intent.putExtra(LibraryConstants.NAME, addressWrapper.toString());
            intent.putExtra(LibraryConstants.LATITUDE, addressWrapper.getAddress().getLatitude());
            intent.putExtra(LibraryConstants.LONGITUDE, addressWrapper.getAddress().getLongitude());

            this.setResult(RESULT_OK, intent);
            finish();
        } else {
            GPDialogs.infoDialog(this, noValidItemSelectedMsg, null);
        }
    }

    private boolean checkNetwork() {
        if (NetworkUtilities.isNetworkAvailable(this)) {
            return true;
        }

        GPDialogs.infoDialog(this, getString(R.string.available_only_with_network), null);
        return false;
    }

    /**
     * routeTo action.
     *
     * @param view parent.
     */
    public void routeTo(View view) {
        if (!checkNetwork()) {
            return;
        }

        if (mListView.getCheckedItemPosition() == ListView.INVALID_POSITION) {
            GPDialogs.infoDialog(this, noValidItemSelectedMsg, null);
            return;
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        final String routing_api_key = preferences.getString("ROUTING_API_KEY", "");
        String[] items;
        if (routing_api_key.length() > 0) {
            items = new String[]{//
                    OSRM, //
                    MAPQUEST, //
                    GRAPHHOPPER //
            };
        } else {
            items = new String[]{//
                    OSRM
            };
        }

        new AlertDialog.Builder(this).setSingleChoiceItems(items, 0, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    private RoadManager roadManager = null;

                    public void onClick(DialogInterface dialog, int whichButton) {

                        dialog.dismiss();

                        ListView preferenceChoiceListView = ((AlertDialog) dialog).getListView();
                        int selectedPosition = preferenceChoiceListView.getCheckedItemPosition();

                        switch (selectedPosition) {
                            case 1:
                                roadManager = new MapQuestRoadManager(routing_api_key);
                                break;
                            case 2:
                                roadManager = new GraphHopperRoadManager(routing_api_key);
                                break;
                            case 0:
                            default:
                                roadManager = new OSRMRoadManager();
                                break;
                        }

                        if (mListView.getCheckedItemPosition() != ListView.INVALID_POSITION) {
                            AddressWrapper addressWrapper = (AddressWrapper) mListView.getItemAtPosition(mListView
                                    .getCheckedItemPosition());
                            final String featureName = addressWrapper.getAddress().getFeatureName();
                            final double latitude = addressWrapper.getAddress().getLatitude();
                            final double longitude = addressWrapper.getAddress().getLongitude();

                            final double[] lonLatZoom = PositionUtilities.getMapCenterFromPreferences(preferences, false, false);
                            final Intent intent = getIntent();

                            orsProgressDialog = ProgressDialog.show(GeocodeActivity.this, getString(R.string.routing_service),
                                    getString(R.string.downloading_route), true, false);
                            new AsyncTask<String, Void, String>() {
                                protected String doInBackground(String... params) {
                                    try {


                                        ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
                                        waypoints.add(new GeoPoint(lonLatZoom[1], lonLatZoom[0]));
                                        waypoints.add(new GeoPoint(latitude, longitude));
                                        Road road = null;
                                        try {
                                            road = roadManager.getRoad(GeocodeActivity.this, waypoints);
                                        } catch (IOException e) {
                                            return e.getMessage();
                                        }
                                        String distance = " (" + ((int) (road.mLength * 10)) / 10.0 + "km )";
                                        ArrayList<GeoPoint> routeNodes = road.mRouteHigh;
                                        float[] routePoints = new float[routeNodes.size() * 2];
                                        int index = 0;
                                        for (GeoPoint routeNode : routeNodes) {
                                            routePoints[index++] = (float) routeNode.getLongitude();
                                            routePoints[index++] = (float) routeNode.getLatitude();
                                        }

                                        intent.putExtra(LibraryConstants.ROUTE, routePoints);
                                        String routeName = getString(R.string.route_to) + featureName + distance;
                                        intent.putExtra(LibraryConstants.NAME, routeName);
                                        return null;
                                    } catch (Exception e) {
                                        GPLog.error(this, null, e);
                                        return getString(R.string.route_extraction_error);
                                    }
                                }

                                protected void onPostExecute(String errorMessage) {
                                    GPDialogs.dismissProgressDialog(orsProgressDialog);
                                    if (errorMessage == null) {
                                        GeocodeActivity.this.setResult(RESULT_OK, intent);
                                        finish();
                                    } else {
                                        GPDialogs.warningDialog(GeocodeActivity.this, errorMessage, null);
                                    }
                                }

                            }.execute((String) null);
                        }
                    }
                }).show();

    }

    private static class AddressWrapper {
        private Address address;

        public AddressWrapper(Address address) {
            this.address = address;
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();

            for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                stringBuilder.append(address.getAddressLine(i));

                if ((i + 1) < address.getMaxAddressLineIndex()) {
                    stringBuilder.append(", "); //$NON-NLS-1$
                }
            }

            return stringBuilder.toString();
        }

        public Address getAddress() {
            return address;
        }
    }
}
