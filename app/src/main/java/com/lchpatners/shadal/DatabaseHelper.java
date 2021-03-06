package com.lchpatners.shadal;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/**
 * Manages the SQLite Database.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    /**
     * Database version.
     */
    private static final int VERSION = 18;

    /**
     * The restaurants table's name.
     */
    private static final String RESTAURANTS = "restaurants";
    /**
     * The menu table's name.
     */
    private static final String MENUS = "menus";
    /**
     * The flyer table's name.
     */
    private static final String FLYERS = "flyers";

    private static final String RESTAURANT_COLUMNS = "(id INTEGER PRIMARY KEY, server_id INT, name TEXT, " +
            "category TEXT, openingHours TEXT, closingHours TEXT, phoneNumber TEXT, has_flyer INTEGER, " +
            "has_coupon INTEGER, is_new INTEGER, is_favorite INTEGER, coupon_string TEXT, updated_at TEXT)";
    private static final String MENU_COLUMNS = "(id INTEGER PRIMARY KEY, menu TEXT, section TEXT, " +
            "price INT, restaurant_id INT)";
    private static final String FLYER_COLUMNS = "(id INTEGER PRIMARY KEY, url TEXT, restaurant_id INT)";

    public static final String LEGACY_DATABASE_NAME = "Shadal";
    /**
     * A list of bookmarks from the old version's database.
     * Each integer value means the server-side id of restaurants.
     */
    public static ArrayList<Integer> legacyBookmarks = new ArrayList<>();


    /**
     * The singleton object.
     */
    private static DatabaseHelper instance;
    /**
     * The campus whose database is currently loaded to be handled..
     */
    private static String loadedCampus;

    private Context context;

    /**
     * If {@link #instance} is null, or {@link #loadedCampus} is different from the
     * user's last pick, instantiate a new object of {@link com.lchpatners.shadal.DatabaseHelper
     * DatabaseHelper}.
     * @param context {@link android.content.Context}
     * @return A {@link com.lchpatners.shadal.DatabaseHelper DatabaseHelper} instance.
     */
    public static DatabaseHelper getInstance(Context context) {
        String selectedCampus = Preferences.getCampusEnglishName(context);
        if (instance == null || (loadedCampus != null && !loadedCampus.equals(selectedCampus))) {
            // Thread-safely
            synchronized (DatabaseHelper.class) {
                loadedCampus = selectedCampus;
                instance = new DatabaseHelper(context, selectedCampus);
            }
        }
        return instance;
    }

    /**
     * Constructs a {@link com.lchpatners.shadal.DatabaseHelper DatabaseHelper}.
     * @param context {@link android.content.Context}
     * @param selectedCampus The campus database to be handled.
     */
    private DatabaseHelper(Context context, String selectedCampus) {
        super(context.getApplicationContext(), selectedCampus, null, VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(String.format("DROP TABLE IF EXISTS %s;", RESTAURANTS));
        db.execSQL(String.format("DROP TABLE IF EXISTS %s;", MENUS));
        db.execSQL(String.format("DROP TABLE IF EXISTS %s;", FLYERS));
        db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s %s;", RESTAURANTS, RESTAURANT_COLUMNS));
        db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s %s;", MENUS, MENU_COLUMNS));
        db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s %s;", FLYERS, FLYER_COLUMNS));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    /**
     * @param dbName The name of database to be checked.
     * @return If the database exists.
     */
    public boolean checkDatabase(String dbName) {
        if (dbName == null) return false;
        File dbFile = context.getDatabasePath(dbName);
        return dbFile.exists();
    }

    /**
     * Insert if new to the table, or otherwise update the existing data.
     * Data are identified by the server-side id value. And then
     * reload {@link com.lchpatners.shadal.RestaurantListFragment#latestAdapter latestAdapter}.
     * @param restaurantJson {@link org.json.JSONObject JSONObject} from {@link com.lchpatners.shadal.Server Server}.
     */
    public void updateRestaurant(JSONObject restaurantJson) {
        updateRestaurant(restaurantJson, null);
        reloadRestaurantListAdapter(RestaurantListFragment.latestAdapter);
    }

    /**
     * Insert if new to the table, or otherwise update the existing data.
     * Data are identified by the server-side id value. And then
     * reload the {@link com.lchpatners.shadal.MenuListActivity activity}.
     * @param restaurantJson {@link org.json.JSONObject JSONObject} from {@link com.lchpatners.shadal.Server Server}.
     * @param activity {@link com.lchpatners.shadal.MenuListActivity MenuListActivity} to reload.
     */
    public void updateRestaurant(JSONObject restaurantJson, final MenuListActivity activity) {
        Cursor cursor = null;
        try {
            int restaurantServerId = restaurantJson.getInt("id");
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values;

            boolean isLegacyBookmark = legacyBookmarks.contains(restaurantJson.getInt("id"));

            values = new ContentValues();
            values.put("server_id", restaurantJson.getInt("id"));
            values.put("updated_at", restaurantJson.getString("updated_at"));
            values.put("name", restaurantJson.getString("name"));
            values.put("phoneNumber", restaurantJson.getString("phone_number"));
            values.put("category", restaurantJson.getString("category").trim());
            values.put("openingHours", restaurantJson.getString("openingHours"));
            values.put("closingHours", restaurantJson.getString("closingHours"));
            values.put("has_flyer", (restaurantJson.getBoolean("has_flyer")) ? 1 : 0);
            values.put("has_coupon", (restaurantJson.getBoolean("has_coupon")) ? 1 : 0);
            values.put("is_new", (restaurantJson.getBoolean("is_new")) ? 1 : 0);
            values.put("is_favorite", isLegacyBookmark ? 1 : 0);
            values.put("coupon_string", restaurantJson.getString("coupon_string"));

            cursor = db.rawQuery(String.format(
                    "SELECT * FROM %s WHERE server_id = %d;",
                    RESTAURANTS, restaurantServerId
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                db.update(RESTAURANTS, values, "server_id = ?", new String[]{String.valueOf(restaurantServerId)});
            } else {
                db.insert(RESTAURANTS, null, values);
                if (isLegacyBookmark) {
                    reloadRestaurantListAdapter(BookmarkFragment.latestAdapter);
                }
            }

            // Update menus and leaflet urls corresponding to the restaurant
            db.execSQL(String.format(
                    "DELETE FROM %s WHERE restaurant_id = %d;",
                    MENUS, restaurantServerId
            ));
            JSONArray menus = restaurantJson.getJSONArray("menus");
            for (int i = 0; i < menus.length(); i++) {
                JSONObject menu = menus.getJSONObject(i);

                values = new ContentValues();
                values.put("menu", menu.getString("name"));
                values.put("section", menu.getString("section"));
                values.put("price", menu.getInt("price"));
                values.put("restaurant_id", restaurantServerId);

                db.insert(MENUS, null, values);
            }

            db.execSQL(String.format(
                    "DELETE FROM %s WHERE restaurant_id = %d;",
                    FLYERS, restaurantServerId
            ));
            JSONArray urls = restaurantJson.getJSONArray("flyers_url");
            for (int i = 0; i < urls.length(); i++) {
                String url = urls.getString(i);
                values = new ContentValues();
                values.put("url", url);
                values.put("restaurant_id", restaurantServerId);
                db.insert(FLYERS, null, values);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            reloadMenuListActivity(activity);
        }
    }

    /**
     * Update the category with a {@link org.json.JSONArray JSONArray}. If a single data was
     * already in the device database, check if the device's data is outdated compared to the
     * new data. Outdated, update the {@link com.lchpatners.shadal.Restaurant Restaurant} data
     * with a {@link com.lchpatners.shadal.Server}.
     * <br><strong>NOTE</strong>: This is because Server API "res_in_category" returns
     * incomplete restaurant data which lacks some fields.
     * Server offering complete data, you could use the JSONObject object
     * retrieved from the Cursor e.g. <code>if (...) { updateRestaurant(jsonObjFromCursor(cursor); }</code>
     * instead of <code>server.updatedRestaurant(...)</code> call.
     * @param restaurants {@link org.json.JSONArray JSONArray} data to update with.
     * @param category A category the restaurants belongs to.
     * @see com.lchpatners.shadal.Server#updateRestaurant(int, java.lang.String) Server.updateRestaurant(int, String)
     */
    public void updateCategory(JSONArray restaurants, String category) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;
        Server server = new Server(context);
        try {
            for (int i = 0; i < restaurants.length(); i++) {
                JSONObject restaurant = restaurants.getJSONObject(i);
                cursor = db.rawQuery(String.format(
                        "SELECT * FROM %s WHERE server_id = %d;",
                        RESTAURANTS, restaurant.getInt("id")
                ), null);
                // If there is an existing data, check if the data is outdated.
                // Else, or if the restaurant is a new one, insert it into the database.
                if (cursor != null && cursor.moveToFirst()) {
                    // If the existing data is outdated, update from the server.
                    // Else, do nothing.
                    if (!restaurant.getString("updated_at")
                            .equals(cursor.getString(cursor.getColumnIndex("updated_at")))) {
                        server.updateRestaurant(restaurant.getInt("id"),
                                cursor.getString(cursor.getColumnIndex("updated_at")));
                    }
                } else {
                    restaurant.put("category", category);
                    restaurant.put("openingHours", "0.0");
                    restaurant.put("closingHours", "0.0");
                    restaurant.put("coupon_string", "loading...");
                    restaurant.put("is_favorite", 0);
                    restaurant.put("menus", new JSONArray());
                    restaurant.put("flyers_url", new JSONArray());
                    restaurant.put("updated_at", "00:00");
                    updateRestaurant(restaurant);
                }
            }

            // Delete restaurants no more available from the server.
            cursor = db.rawQuery(String.format(
                    "SELECT * FROM %s WHERE category = '%s';",
                    RESTAURANTS, category
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    // I'm making a note here, "Huge Success!"
                    boolean stillAlive = false;
                    for (int i = 0; i < restaurants.length(); i++) {
                        JSONObject restaurant = restaurants.getJSONObject(i);
                        if (restaurant.getInt("id") == cursor.getInt(cursor.getColumnIndex("server_id"))) {
                            stillAlive = true;
                            break;
                        }
                    }
                    if (!stillAlive) {
                        db.delete(RESTAURANTS, "server_id = ?", new String[]{
                                String.valueOf(cursor.getInt(cursor.getColumnIndex("server_id")))
                        });
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            reloadRestaurantListAdapter(RestaurantListFragment.latestAdapter);
        }
    }


    /**
     * @return Bookmarked restaurants.
     */
    public ArrayList<Restaurant> getFavoriteRestaurants() {
        ArrayList<Restaurant> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            for (String category : CategoryListAdapter.categories) {
                cursor = db.rawQuery(String.format(
                        "SELECT * FROM %s WHERE is_favorite = 1 AND category ='%s' ORDER BY has_flyer DESC, name ASC;",
                        RESTAURANTS, category
                ), null);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        list.add(new Restaurant(cursor));
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * Bookmark a restaurant if it wasn't, and do the opposite otherwise.
     * @param restaurantId The restaurant's server-side id.
     * @return <code>true</code> if it was bookmarked,
     * <code>false</code> if un-bookmarked.
     */
    public boolean toggleFavoriteById(long restaurantId){
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        Restaurant restaurant = getRestaurantFromId(restaurantId);
        values.put("is_favorite", (!restaurant.isFavorite()) ? 1 : 0);
        db.update(RESTAURANTS, values, "id = " + restaurantId, null);
        reloadRestaurantListAdapter(BookmarkFragment.latestAdapter);
        return !restaurant.isFavorite();
    }

    /**
     * @param category Category to search by.
     * @return Restaurants of the category.
     */
    public ArrayList<Restaurant> getRestaurantsByCategory(String category) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<Restaurant> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(String.format(
                    "SELECT * FROM %s WHERE category = '%s' ORDER BY has_flyer DESC, name ASC;",
                    RESTAURANTS, category
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    list.add(new Restaurant(cursor));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * @param restaurantServerId The restaurant's server-side id.
     * @return Menu data of a restaurant.
     */
    public ArrayList<Menu> getMenusByRestaurantServerId(long restaurantServerId) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<Menu> list = new ArrayList<>();
        ArrayList<String> sections = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(String.format(
                   "SELECT section FROM %s WHERE restaurant_id = %d;",
                    MENUS, restaurantServerId
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String section = cursor.getString(cursor.getColumnIndex("section"));
                    if (!sections.contains(section)) {
                        sections.add(section);
                    }
                } while (cursor.moveToNext());
            }
            for (String section : sections) {
                cursor = db.rawQuery(String.format(
                        "SELECT * FROM %s WHERE restaurant_id = %d AND section = '%s';",
                        MENUS, restaurantServerId, section
                ), null);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        Menu menu = new Menu();
                        menu.setId(cursor.getInt((cursor.getColumnIndex("id"))));
                        menu.setItem((cursor.getString(cursor.getColumnIndex("menu"))));
                        menu.setSection(cursor.getString(cursor.getColumnIndex("section")));
                        menu.setPrice(cursor.getInt(cursor.getColumnIndex("price")));
                        menu.setRestaurantId(cursor.getInt(cursor.getColumnIndex("restaurant_id")));
                        list.add(menu);
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * @param restaurantServerId The restaurant's server-side id.
     * @return Flyer urls of a restaurant.
     */
    public ArrayList<String> getFlyerUrlsByRestaurantServerId(long restaurantServerId) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<String> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(String.format(
                    "SELECT * FROM %s WHERE restaurant_id = %d;",
                    FLYERS, restaurantServerId
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    list.add(cursor.getString(cursor.getColumnIndex("url")));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * A WILD RESTAURANT APPEARS!
     * @return A randomly selected restaurant.
     */
    public Restaurant getRandomRestaurant() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        Restaurant restaurant = null;
        try {
            cursor = db.rawQuery(String.format(
                    "SELECT * FROM %s ORDER BY RANDOM() LIMIT 1;",
                    RESTAURANTS
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                restaurant = new Restaurant(cursor);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return restaurant;
    }

    /**
     * @param id The restaurant's server-side id.
     * @return A restaurant with the <code>id</code>.
     */
    public Restaurant getRestaurantFromId(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        Restaurant restaurant = null;
        try {
            cursor = db.rawQuery(String.format(
                    "SELECT * FROM %s WHERE id = %d;",
                    RESTAURANTS, id
            ), null);
            if (cursor != null && cursor.moveToFirst()) {
                restaurant = new Restaurant(cursor);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return restaurant;
    }

    /**
     * Reload a {@link com.lchpatners.shadal.RestaurantListAdapter adapter}.
     * @param adapter An adapter to be reloaded.
     */
    public void reloadRestaurantListAdapter(final RestaurantListAdapter adapter) {
        if (adapter != null) {
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.reloadData();
                }
            });
        }
    }

    /**
     * Reload a {@link com.lchpatners.shadal.MenuListActivity activity}.
     * @param activity An activity to be reloaded.
     */
    public void reloadMenuListActivity(final MenuListActivity activity) {
        if (activity != null) {
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.setView();
                }
            });
        }
    }

}
