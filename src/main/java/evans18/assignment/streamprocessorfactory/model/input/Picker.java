package evans18.assignment.streamprocessorfactory.model.input;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * Describes a picker worker.
 * <p>
 * Used in expected input.
 */
public class Picker {
    private final String id;
    public final String name;
    @SerializedName("active_since")
    private final Date activeSince;

    public Picker(String id, String name, Date activeSince) {
        this.id = id;
        this.name = name;
        this.activeSince = activeSince;
    }

    public String getId() {
        return id;
    }

    public Date getActiveSince() {
        return activeSince;
    }
}


