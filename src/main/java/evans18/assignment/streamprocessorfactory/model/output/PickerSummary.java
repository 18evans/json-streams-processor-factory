package evans18.assignment.streamprocessorfactory.model.output;

import com.google.gson.annotations.SerializedName;
import evans18.assignment.streamprocessorfactory.model.input.Article;
import evans18.assignment.streamprocessorfactory.model.input.Picker;
import evans18.assignment.streamprocessorfactory.model.input.PickerEvent;

import java.util.Date;

/**
 * Describes a summary of the articles picked up by a picker.
 * <p>
 * Model with relations of that a {@link Picker} has with multiple {@link Article}
 * as seen in {@link #picks}.
 * <p>
 * Used in expected output.
 */
public class PickerSummary {
    @SerializedName("picker_name")
    public final String pickerName;
    @SerializedName("active_since")
    public final Date activeSince;
    public final ArticlePick[] picks;

    /**
     * @param pickerName  from {@link Picker}.
     * @param activeSince from {@link Picker}.
     * @param picks       from {@link PickerEvent}.
     */
    public PickerSummary(String pickerName, Date activeSince, ArticlePick[] picks) {
        this.pickerName = pickerName;
        this.activeSince = activeSince;
        this.picks = picks;
    }
}
