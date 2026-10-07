package dev.chronolink.v2.core;

import java.util.ArrayList;
import java.util.List;

/** Live counts may refresh without invalidating a click on an unchanged resource identity. */
public final class SelectionRevision<K> {
    private int revision;
    private String context;
    private List<K> visible = new ArrayList<K>();
    public int update(String context, List<K> rows) {
        if (!context.equals(this.context) || !visible.equals(rows)) {
            this.context = context;
            visible = new ArrayList<K>(rows);
            revision = revision == Integer.MAX_VALUE ? 1 : revision + 1;
        }
        return revision;
    }
    public boolean accepts(int received) { return revision > 0 && received == revision; }
}
