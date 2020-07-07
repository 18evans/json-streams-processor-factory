package evans18.assignment.streamprocessorfactory.model.input;

import com.google.gson.annotations.SerializedName;

/**
 * Describes an item.
 * <p>
 * Used in expected input.
 */
public class Article {
    public static final String TEMPERATURE_ZONE_AMBIENT = "ambient";
//    public static final String TEMPERATURE_ZONE_CHILLED = "chilled";

    public final String id;
    public final String name;
    @SerializedName("temperature_zone")
    public final String temperatureZone;

    public Article(String id, String name, String temperatureZone) {
        this.id = id;
        this.name = name;
        this.temperatureZone = temperatureZone;
    }

}


