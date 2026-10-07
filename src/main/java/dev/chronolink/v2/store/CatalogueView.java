package dev.chronolink.v2.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;

/** Read-only, stable pages. Recent flows and existing selections remain visible at zero stock. */
public final class CatalogueView {
    public static final int PAGE_SIZE = 6;
    private CatalogueView() {}
    public static final class Row {
        public final Key key;
        public final long count;
        public final String label, id, order;
        Row(Key key, long count) {
            this.key = key; this.count = Math.max(0, count);
            label = key.label(); id = identifier(key);
            String tags = key.item != null ? String.valueOf(key.item.stackTagCompound) :
                key.fluid != null ? String.valueOf(key.fluid.tag) : "";
            order = label.toLowerCase(Locale.ROOT) + "\u0000" + id + "\u0000" + tags;
        }
    }
    public static final class Page {
        public final List<Row> rows;
        public final int index, pages, total;
        Page(List<Row> rows, int index, int pages, int total) {
            this.rows = rows; this.index = index; this.pages = pages; this.total = total;
        }
    }
    public static String identifier(Key key) {
        if (key.kind == Key.ITEM) return String.valueOf(Item.itemRegistry.getNameForObject(key.item.getItem())) + ":" + key.item.getItemDamage();
        return key.isFluid() ? key.fluid.getFluid().getName() : key.kind == Key.EU ? "EU" : "RF";
    }
    public static String cleanQuery(String value) {
        if (value == null) return "";
        String result = value.replaceAll("[\\p{Cntrl}§]", "").trim();
        return result.substring(0, Math.min(64, result.length()));
    }
    private static boolean group(Key key, int group) { return key != null && (group == 0 ? key.kind == Key.ITEM : key.isFluid()); }
    public static Page page(Map<Key,Long> stock, Iterable<Key> recent, Iterable<Key> selected, int group, String query, int page) {
        Map<Key,Long> merged = new LinkedHashMap<Key,Long>();
        for (Map.Entry<Key,Long> entry : stock.entrySet()) if (group(entry.getKey(),group)) merged.put(entry.getKey(), Math.max(0,entry.getValue()));
        for (Key key : recent) if (group(key,group) && !merged.containsKey(key)) merged.put(key,0L);
        for (Key key : selected) if (group(key,group) && !merged.containsKey(key)) merged.put(key,0L);
        String normalized = cleanQuery(query).toLowerCase(Locale.ROOT);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        List<Row> result = new ArrayList<Row>();
        for (Map.Entry<Key,Long> entry : merged.entrySet()) {
            Row row = new Row(entry.getKey(),entry.getValue());
            String text = (row.label + " " + row.id).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : words) if (!text.contains(word)) { matches=false; break; }
            if (matches) result.add(row);
        }
        Collections.sort(result, new Comparator<Row>() {
            @Override public int compare(Row a,Row b) { return a.order.compareTo(b.order); }
        });
        int pages = Math.max(1,(result.size()+PAGE_SIZE-1)/PAGE_SIZE);
        int index = Math.max(0,Math.min(page,pages-1));
        return new Page(new ArrayList<Row>(result.subList(index*PAGE_SIZE, Math.min(result.size(),(index+1)*PAGE_SIZE))),index,pages,result.size());
    }
}
