package com.lchpatners.fragments;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.lchpatners.apphelper.server.Server;
import com.lchpatners.shadal.MainActivity;
import com.lchpatners.shadal.R;
import com.lchpatners.views.NamsanTextView;

import java.util.ArrayList;

import info.android.sqlite.helper.DatabaseHelper;
import info.android.sqlite.model.Restaurant;

/**
 * Created by Gwangrae Kim on 2014-08-30.
 */
public class RestaurantsFragment extends Fragment implements ActionBarUpdater, Locatable {
    private final static String EXTRA_CATEGORY_IDX = "catIdx";
    private final static String TAG = "RestaurantsFragment";
    public String tag() {
        return TAG;
    }

    private int attachedPage = -1;
    public int getAttachedPage() {
        return attachedPage;
    }
    public void setAttachedPage(int page) {
        this.attachedPage = page;
    }

    public static class RestaurantsAdapter extends ArrayAdapter<Restaurant> {
        private static class RestaurantViewHolder {
            public TextView restaurantName;
            public ImageView flyer, favorite, coupon, newRestaurant;
        }

        private final MainActivity mActivity;
        private final ArrayList<Restaurant> values;
        private final LayoutInflater mInflater;

        private int TAB_TO_ATTACH_MENU_FRAGMENT = MainActivity.TAB_MAIN;

        /**
         * By Default, this class will attach MenuFragment to MainActivity.TAB_MAIN
         */
        public RestaurantsAdapter(MainActivity activity, ArrayList<Restaurant> restaurants) {
            super(activity, R.layout.listview_item_restaurant, restaurants);
            this.mActivity = activity;
            this.values = restaurants;
            this.mInflater = LayoutInflater.from(activity);
        }

        public RestaurantsAdapter(MainActivity activity, ArrayList<Restaurant> restaurants,
                                 int tabToAttachMenuFragment) {
            this (activity, restaurants);
            this.TAB_TO_ATTACH_MENU_FRAGMENT = tabToAttachMenuFragment;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            TextView restaurantName;
            ImageView flyer, favorite, coupon, newRestaurant;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.listview_item_restaurant, null);
                restaurantName = (TextView) convertView.findViewById(R.id.restaurant_name);
                flyer = (ImageView) convertView.findViewById(R.id.flyer);
                favorite = (ImageView) convertView.findViewById(R.id.favorite);
                coupon = (ImageView) convertView.findViewById(R.id.coupon);
                newRestaurant = (ImageView) convertView.findViewById(R.id.newRestaurant);

                RestaurantViewHolder viewHolder = new RestaurantViewHolder();
                viewHolder.flyer = flyer;
                viewHolder.favorite = favorite;
                viewHolder.coupon = coupon;
                viewHolder.newRestaurant = newRestaurant;

                viewHolder.restaurantName = restaurantName;
                convertView.setTag(viewHolder);
            }
            else {
                RestaurantViewHolder viewHolder = (RestaurantViewHolder) convertView.getTag();
                restaurantName = viewHolder.restaurantName;
                flyer = viewHolder.flyer;
                favorite = viewHolder.favorite;
                coupon = viewHolder.coupon;
                newRestaurant = viewHolder.newRestaurant;
            }

            final Restaurant restaurant = getItem(position);
            restaurantName.setText(restaurant.name);
            if(restaurant.hasFlyer()) {
                flyer.setVisibility(View.VISIBLE);
            }
            if(restaurant.isFavorite()) {
                favorite.setVisibility(View.VISIBLE);
            }
            if(restaurant.getCoupon()) {
                coupon.setVisibility(View.VISIBLE);
            }
            if(restaurant.isNew()) {
                newRestaurant.setVisibility(View.VISIBLE);
            }

            convertView.setOnClickListener(new View.OnClickListener () {
                @Override
                public void onClick(View v) {
                    //Log.e("tag", "called");
                    MainActivity.ShadalTabsAdapter adapter = mActivity.getAdapter();
                    adapter.push(TAB_TO_ATTACH_MENU_FRAGMENT,
                            new MainActivity.ShadalTabsAdapter.FragmentRecord(MenuFragment.class, restaurant.id));
                }
            });

            if(position %2 == 0) convertView.setBackgroundColor(0xfff2f2f2);
            else convertView.setBackgroundColor(0xfffcfcfc);

            return convertView;
        }

    }

    public static RestaurantsFragment newInstance(int categoryIndex) {
        RestaurantsFragment rf = new RestaurantsFragment();
        Bundle bdl = new Bundle(1);
        bdl.putInt(EXTRA_CATEGORY_IDX, categoryIndex);
        rf.setArguments(bdl);
        return rf;
    }

    private RestaurantsAdapter adapter;
    private ArrayList<Restaurant> mResults = new ArrayList<Restaurant>();
    private DatabaseHelper db;
    private String mCategoryName;
    private Activity mActivity;
    private boolean updateActionBarOnCreateView = false;

    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int categoryIndex = getArguments().getInt(EXTRA_CATEGORY_IDX);
        this.mCategoryName = getResources().getStringArray(R.array.categories)[categoryIndex];
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mActivity = getActivity();
        if (updateActionBarOnCreateView)
            updateActionBar();

        ListView resultView = (ListView) inflater.inflate(R.layout.fragment_restaurant, container, false);

        //Query database
        db = new DatabaseHelper(mActivity);
        mResults = db.getAllRestaurantWithCategoryInOrder(mCategoryName);

        adapter = new RestaurantsAdapter((MainActivity) mActivity, mResults);
        resultView.setAdapter(adapter);
        db.closeDB();

        // check for update
        try {
            Server server = new Server(mActivity);
            server.updateRestaurantInCategory(mCategoryName, adapter);
        }
        catch (Exception e) {
            Log.e(TAG, "Error while communicating with server.",e);
        }
        return resultView;
    }

    public void setUpdateActionBarOnCreateView() {
        this.updateActionBarOnCreateView = true;
    }

    public void updateActionBar () {
        ActionBar actionBar = mActivity.getActionBar();
        //actionBar.setDisplayHomeAsUpEnabled(true);
        ViewGroup titleBar = (ViewGroup) mActivity.getLayoutInflater().inflate(R.layout.action_bar_restaurant, null);
        titleBar.setLayoutParams(actionBar.getCustomView().getLayoutParams());
        NamsanTextView title = (NamsanTextView) titleBar.findViewById(R.id.textview_restaurant_title);
        title.setText(mCategoryName);
        actionBar.setCustomView(titleBar);
        mActivity.invalidateOptionsMenu();
    }

}