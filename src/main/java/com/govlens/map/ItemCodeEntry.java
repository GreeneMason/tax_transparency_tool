package com.govlens.map;

/** Response DTO for a distinct item code entry used to populate the map category dropdown. */
public class ItemCodeEntry {

    private final String itemCode;
    private final String itemDescription;

    public ItemCodeEntry(String itemCode, String itemDescription) {
        this.itemCode        = itemCode;
        this.itemDescription = itemDescription;
    }

    public String getItemCode()        { return itemCode; }
    public String getItemDescription() { return itemDescription; }
}
