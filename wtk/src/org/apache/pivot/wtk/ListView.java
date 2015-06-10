/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pivot.wtk;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;

import org.apache.pivot.beans.DefaultProperty;
import org.apache.pivot.collections.ArrayList;
import org.apache.pivot.collections.List;
import org.apache.pivot.collections.ListListener;
import org.apache.pivot.collections.Map;
import org.apache.pivot.collections.Sequence;
import org.apache.pivot.collections.immutable.ImmutableList;
import org.apache.pivot.json.JSON;
import org.apache.pivot.json.JSONSerializer;
import org.apache.pivot.serialization.SerializationException;
import org.apache.pivot.util.Filter;
import org.apache.pivot.util.ListenerList;
import org.apache.pivot.util.Utils;
import org.apache.pivot.wtk.content.ListViewItemRenderer;

/**
 * Component that displays a sequence of items, optionally allowing a user to
 * select or check one or more items.
 */
@DefaultProperty("listData")
public class ListView extends Component {
    /**
     * Enumeration defining supported selection modes.
     */
    public enum SelectMode {
        /**
         * Selection is disabled.
         */
        NONE,

        /**
         * A single index may be selected at a time.
         */
        SINGLE,

        /**
         * Multiple indexes may be concurrently selected.
         */
        MULTI
    }

    /**
     * {@link Renderer} interface to customize the appearance of items in a
     * ListView.
     */
    public interface ItemRenderer extends Renderer {
        /**
         * Prepares the renderer for layout or paint.
         *
         * @param item The item to render, or <tt>null</tt> if called to
         * calculate preferred height for skins that assume a fixed renderer
         * height.
         * @param index The index of the item being rendered, or <tt>-1</tt> if
         * <tt>item</tt> is <tt>null</tt>.
         * @param listView The host component.
         * @param selected If <tt>true</tt>, the item is selected. the item.
         * @param state The selected, unselected or mixed state for the checkmark.
         * If tri-state checkmarks are not enabled, this value will either be
         * selected or unselected.
         * @param highlighted If <tt>true</tt>, the item is highlighted.
         * @param disabled If <tt>true</tt>, the item is disabled.
         */
        public void render(Object item, int index, ListView listView, boolean selected,
            Button.State state, boolean highlighted, boolean disabled);

        /**
         * Converts a list item to a string representation.
         *
         * @param item
         * @return The item's string representation, or <tt>null</tt> if the item
         * does not have a string representation. <p> Note that this method may
         * be called often during keyboard navigation, so implementations should
         * avoid unnecessary string allocations.
         */
        public String toString(Object item);
    }

    /**
     * List view item editor interface.
     */
    public interface ItemEditor {
        /**
         * Called to begin editing a list item.
         *
         * @param listView
         * @param itemIndex
         */
        public void beginEdit(ListView listView, int itemIndex);

        /**
         * Terminates an edit operation.
         *
         * @param result <tt>true</tt> to perform the edit; <tt>false</tt> to
         * cancel it.
         */
        public void endEdit(boolean result);

        /**
         * Tests whether an edit is currently in progress.
         */
        public boolean isEditing();
    }

    /**
     * List view skin interface. List view skins are required to implement this.
     */
    public interface Skin {
        public int getItemAt(int y);

        public Bounds getItemBounds(int index);

        public int getItemIndent();
    }

    /**
     * Translates between list and bind context data during data binding.
     */
    public interface ListDataBindMapping {
        /**
         * Converts a context value to list data during a
         * {@link Component#load(Object)} operation.
         *
         * @param value
         */
        public List<?> toListData(Object value);

        /**
         * Converts list data to a context value during a
         * {@link Component#store(Object)} operation.
         *
         * @param listData
         */
        public Object valueOf(List<?> listData);
    }

    /**
     * Translates between item position and bind context data during data
     * binding.
     */
    public interface ItemBindMapping {
        /**
         * Returns the index of the item in the source list during a
         * {@link Component#load(Object)} operation.
         *
         * @param listData The source list data.
         * @param value The value to locate.
         * @return The index of first occurrence of the value if it exists in the
         * list; <tt>-1</tt>, otherwise.
         */
        public int indexOf(List<?> listData, Object value);

        /**
         * Retrieves the value at the given index during a
         * {@link Component#store(Object)} operation.
         *
         * @param listData The source list data.
         * @param index The index of the value to retrieve.
         */
        public Object get(List<?> listData, int index);
    }

    /**
     * Translates between item position and bind context data as well
     * as item state during data binding.
     */
    public interface ItemStateBindMapping extends ItemBindMapping {
        /**
         * Returns the {@link Button$State} for the given item during a
         * {@link Component#load(Object)} operation.
         *
         * @param item The list item whose state we need.
         * @return The {@link Button$State} for the given item.
         */
        public Button.State getState(Object item);

        /**
         * Sets the {@link Button$State} for the given item during a
         * {@link Component#store(Object)} operation.
         *
         * @param item The list item whose state we are going to set.
         * @param state The {@link Button$State} for the given item.
         */
        public void setState(Object item, Button.State state);
    }

    private static class ListViewListenerList extends WTKListenerList<ListViewListener> implements
        ListViewListener {
        @Override
        public void listDataChanged(ListView listView, List<?> previousListData) {
            for (ListViewListener listener : this) {
                listener.listDataChanged(listView, previousListData);
            }
        }

        @Override
        public void itemRendererChanged(ListView listView,
            ListView.ItemRenderer previousItemRenderer) {
            for (ListViewListener listener : this) {
                listener.itemRendererChanged(listView, previousItemRenderer);
            }
        }

        @Override
        public void itemEditorChanged(ListView listView, ListView.ItemEditor previousItemEditor) {
            for (ListViewListener listener : this) {
                listener.itemEditorChanged(listView, previousItemEditor);
            }
        }

        @Override
        public void selectModeChanged(ListView listView, ListView.SelectMode previousSelectMode) {
            for (ListViewListener listener : this) {
                listener.selectModeChanged(listView, previousSelectMode);
            }
        }

        @Override
        public void checkmarksEnabledChanged(ListView listView) {
            for (ListViewListener listener : this) {
                listener.checkmarksEnabledChanged(listView);
            }
        }

        @Override
        public void checkmarksTriStateChanged(ListView listView) {
            for (ListViewListener listener : this) {
                listener.checkmarksTriStateChanged(listView);
            }
        }

        @Override
        public void checkmarksMixedAsCheckedChanged(ListView listView) {
            for (ListViewListener listener : this) {
                listener.checkmarksMixedAsCheckedChanged(listView);
            }
        }

        @Override
        public void disabledItemFilterChanged(ListView listView,
            Filter<?> previousDisabledItemFilter) {
            for (ListViewListener listener : this) {
                listener.disabledItemFilterChanged(listView, previousDisabledItemFilter);
            }
        }

        @Override
        public void disabledCheckmarkFilterChanged(ListView listView,
            Filter<?> previousDisabledCheckmarkFilter) {
            for (ListViewListener listener : this) {
                listener.disabledCheckmarkFilterChanged(listView, previousDisabledCheckmarkFilter);
            }
        }
    }

    private static class ListViewItemListenerList extends WTKListenerList<ListViewItemListener>
        implements ListViewItemListener {
        @Override
        public void itemInserted(ListView listView, int index) {
            for (ListViewItemListener listener : this) {
                listener.itemInserted(listView, index);
            }
        }

        @Override
        public void itemsRemoved(ListView listView, int index, int count) {
            for (ListViewItemListener listener : this) {
                listener.itemsRemoved(listView, index, count);
            }
        }

        @Override
        public void itemUpdated(ListView listView, int index) {
            for (ListViewItemListener listener : this) {
                listener.itemUpdated(listView, index);
            }
        }

        @Override
        public void itemsCleared(ListView listView) {
            for (ListViewItemListener listener : this) {
                listener.itemsCleared(listView);
            }
        }

        @Override
        public void itemsSorted(ListView listView) {
            for (ListViewItemListener listener : this) {
                listener.itemsSorted(listView);
            }
        }
    }

    private static class ListViewItemStateListenerList extends
        WTKListenerList<ListViewItemStateListener> implements ListViewItemStateListener {
        @Override
        public void itemCheckedChanged(ListView listView, int index) {
            for (ListViewItemStateListener listener : this) {
                listener.itemCheckedChanged(listView, index);
            }
        }

        @Override
        public void itemCheckedStateChanged(ListView listView, int index) {
            for (ListViewItemStateListener listener : this) {
                listener.itemCheckedStateChanged(listView, index);
            }
        }
    }

    private static class ListViewSelectionListenerList extends
        WTKListenerList<ListViewSelectionListener> implements ListViewSelectionListener {
        @Override
        public void selectedRangeAdded(ListView listView, int rangeStart, int rangeEnd) {
            for (ListViewSelectionListener listener : this) {
                listener.selectedRangeAdded(listView, rangeStart, rangeEnd);
            }
        }

        @Override
        public void selectedRangeRemoved(ListView listView, int rangeStart, int rangeEnd) {
            for (ListViewSelectionListener listener : this) {
                listener.selectedRangeRemoved(listView, rangeStart, rangeEnd);
            }
        }

        @Override
        public void selectedRangesChanged(ListView listView, Sequence<Span> previousSelection) {
            for (ListViewSelectionListener listener : this) {
                listener.selectedRangesChanged(listView, previousSelection);
            }
        }

        @Override
        public void selectedItemChanged(ListView listView, Object previousSelectedItem) {
            for (ListViewSelectionListener listener : this) {
                listener.selectedItemChanged(listView, previousSelectedItem);
            }
        }
    }

    private static class ListViewBindingListenerList extends
        WTKListenerList<ListViewBindingListener> implements ListViewBindingListener {
        @Override
        public void listDataKeyChanged(ListView listView, String previousListDataKey) {
            for (ListViewBindingListener listener : this) {
                listener.listDataKeyChanged(listView, previousListDataKey);
            }
        }

        @Override
        public void listDataBindTypeChanged(ListView listView, BindType previousListDataBindType) {
            for (ListViewBindingListener listener : this) {
                listener.listDataBindTypeChanged(listView, previousListDataBindType);
            }
        }

        @Override
        public void listDataBindMappingChanged(ListView listView,
            ListView.ListDataBindMapping previousListDataBindMapping) {
            for (ListViewBindingListener listener : this) {
                listener.listDataBindMappingChanged(listView, previousListDataBindMapping);
            }
        }

        @Override
        public void selectedItemKeyChanged(ListView listView, String previousSelectedItemKey) {
            for (ListViewBindingListener listener : this) {
                listener.selectedItemKeyChanged(listView, previousSelectedItemKey);
            }
        }

        @Override
        public void selectedItemBindTypeChanged(ListView listView,
            BindType previousSelectedItemBindType) {
            for (ListViewBindingListener listener : this) {
                listener.selectedItemBindTypeChanged(listView, previousSelectedItemBindType);
            }
        }

        @Override
        public void selectedItemBindMappingChanged(ListView listView,
            ItemBindMapping previousSelectedItemBindMapping) {
            for (ListViewBindingListener listener : this) {
                listener.selectedItemBindMappingChanged(listView, previousSelectedItemBindMapping);
            }
        }

        @Override
        public void selectedItemsKeyChanged(ListView listView, String previousSelectedItemsKey) {
            for (ListViewBindingListener listener : this) {
                listener.selectedItemsKeyChanged(listView, previousSelectedItemsKey);
            }
        }

        @Override
        public void selectedItemsBindTypeChanged(ListView listView,
            BindType previousSelectedItemsBindType) {
            for (ListViewBindingListener listener : this) {
                listener.selectedItemsBindTypeChanged(listView, previousSelectedItemsBindType);
            }
        }

        @Override
        public void selectedItemsBindMappingChanged(ListView listView,
            ItemBindMapping previousSelectedItemsBindMapping) {
            for (ListViewBindingListener listener : this) {
                listener.selectedItemsBindMappingChanged(listView, previousSelectedItemsBindMapping);
            }
        }

        @Override
        public void checkedItemsKeyChanged(ListView listView, String previousCheckedItemsKey) {
            for (ListViewBindingListener listener : this) {
                listener.checkedItemsKeyChanged(listView, previousCheckedItemsKey);
            }
        }

        @Override
        public void checkedItemsBindTypeChanged(ListView listView,
            BindType previousCheckedItemsBindType) {
            for (ListViewBindingListener listener : this) {
                listener.checkedItemsBindTypeChanged(listView, previousCheckedItemsBindType);
            }
        }

        @Override
        public void checkedItemsBindMappingChanged(ListView listView,
            ListView.ItemBindMapping previousCheckedItemsBindMapping) {
            for (ListViewBindingListener listener : this) {
                listener.checkedItemsBindMappingChanged(listView, previousCheckedItemsBindMapping);
            }
        }

        @Override
        public void itemsStateKeyChanged(ListView listView, String previousItemsStateKey) {
            for (ListViewBindingListener listener : this) {
                listener.itemsStateKeyChanged(listView, previousItemsStateKey);
            }
        }

        @Override
        public void itemsStateBindTypeChanged(ListView listView, BindType previousItemsStateBindType) {
            for (ListViewBindingListener listener : this) {
                listener.itemsStateBindTypeChanged(listView, previousItemsStateBindType);
            }
        }

        @Override
        public void itemsStateBindMappingChanged(ListView listView,
            ListView.ItemStateBindMapping previousItemsStateBindMapping) {
            for (ListViewBindingListener listener : this) {
                listener.itemsStateBindMappingChanged(listView, previousItemsStateBindMapping);
            }
        }
    }

    private List<?> listData = null;

    private ItemRenderer itemRenderer = null;
    private ItemEditor itemEditor = null;

    private RangeSelection rangeSelection = new RangeSelection();
    private SelectMode selectMode = SelectMode.SINGLE;

    private boolean checkmarksEnabled = false;
    private ArrayList<Integer> checkedIndexes = new ArrayList<>();

    private boolean allowTriStateCheckmarks = false;
    private boolean checkmarksMixedAsChecked = false;
    private ArrayList<Integer> mixedIndexes = new ArrayList<>();

    private Filter<?> disabledItemFilter = null;
    private Filter<?> disabledCheckmarkFilter = null;

    private String listDataKey = null;
    private BindType listDataBindType = BindType.BOTH;
    private ListDataBindMapping listDataBindMapping = null;

    private String selectedItemKey = null;
    private BindType selectedItemBindType = BindType.BOTH;
    private ItemBindMapping selectedItemBindMapping = null;

    private String selectedItemsKey = null;
    private BindType selectedItemsBindType = BindType.BOTH;
    private ItemBindMapping selectedItemsBindMapping = null;

    private String checkedItemsKey = null;
    private BindType checkedItemsBindType = BindType.BOTH;
    private ItemBindMapping checkedItemsBindMapping = null;

    private String itemsStateKey = null;
    private BindType itemsStateBindType = BindType.BOTH;
    private ItemStateBindMapping itemsStateBindMapping = null;

    private ListListener<Object> listDataListener = new ListListener<Object>() {
        @Override
        public void itemInserted(List<Object> list, int index) {
            // Increment selected ranges
            int updated = rangeSelection.insertIndex(index);

            // Increment checked and mixed indexes
            int i = ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index));
            if (i < 0) {
                i = -(i + 1);
            }

            int n = checkedIndexes.getLength();
            while (i < n) {
                checkedIndexes.update(i, Integer.valueOf(checkedIndexes.get(i).intValue() + 1));
                i++;
            }

            if (allowTriStateCheckmarks) {
                i = ArrayList.binarySearch(mixedIndexes, Integer.valueOf(index));
                if (i < 0) {
                    i = -(i + 1);
                }

                n = mixedIndexes.getLength();
                while (i < n) {
                    mixedIndexes.update(i, Integer.valueOf(mixedIndexes.get(i).intValue() + 1));
                    i++;
                }
            }

            // Notify listeners that items were inserted
            listViewItemListeners.itemInserted(ListView.this, index);

            if (updated > 0) {
                listViewSelectionListeners.selectedRangesChanged(ListView.this, getSelectedRanges());
            }
        }

        @Override
        public void itemsRemoved(List<Object> list, int index, Sequence<Object> items) {
            int count = items.getLength();

            int previousSelectedIndex;
            if (selectMode == SelectMode.SINGLE && rangeSelection.getLength() > 0) {
                previousSelectedIndex = rangeSelection.get(0).start;
            } else {
                previousSelectedIndex = -1;
            }

            // Decrement selected ranges
            int updated = rangeSelection.removeIndexes(index, count);

            // Remove and decrement checked and mixed indexes
            int i = ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index));
            if (i < 0) {
                i = -(i + 1);
            }

            int j = ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index + count - 1));
            if (j < 0) {
                j = -(j + 1);
            } else {
                j++;
            }

            checkedIndexes.remove(i, j - i);

            int n = checkedIndexes.getLength();
            while (i < n) {
                checkedIndexes.update(i, Integer.valueOf(checkedIndexes.get(i).intValue() - count));
                i++;
            }

            if (allowTriStateCheckmarks) {
                i = ArrayList.binarySearch(mixedIndexes, Integer.valueOf(index));
                if (i < 0) {
                    i = -(i + 1);
                }

                j = ArrayList.binarySearch(mixedIndexes, Integer.valueOf(index + count - 1));
                if (j < 0) {
                    j = -(j + 1);
                } else {
                    j++;
                }

                mixedIndexes.remove(i, j - i);

                n = mixedIndexes.getLength();
                while (i < n) {
                    mixedIndexes.update(i, Integer.valueOf(mixedIndexes.get(i).intValue() - count));
                    i++;
                }
            }

            // Notify listeners that items were removed
            listViewItemListeners.itemsRemoved(ListView.this, index, count);

            if (updated > 0) {
                listViewSelectionListeners.selectedRangesChanged(ListView.this, getSelectedRanges());

                if (selectMode == SelectMode.SINGLE && getSelectedIndex() != previousSelectedIndex) {
                    listViewSelectionListeners.selectedItemChanged(ListView.this, null);
                }
            }
        }

        @Override
        public void itemUpdated(List<Object> list, int index, Object previousItem) {
            listViewItemListeners.itemUpdated(ListView.this, index);
        }

        @Override
        public void listCleared(List<Object> list) {
            int cleared = rangeSelection.getLength();
            rangeSelection.clear();
            checkedIndexes.clear();
            mixedIndexes.clear();

            listViewItemListeners.itemsCleared(ListView.this);

            if (cleared > 0) {
                listViewSelectionListeners.selectedRangesChanged(ListView.this, getSelectedRanges());

                if (selectMode == SelectMode.SINGLE) {
                    listViewSelectionListeners.selectedItemChanged(ListView.this, null);
                }
            }
        }

        @Override
        public void comparatorChanged(List<Object> list, Comparator<Object> previousComparator) {
            if (list.getComparator() != null) {
                int cleared = rangeSelection.getLength();
                rangeSelection.clear();
                checkedIndexes.clear();
                mixedIndexes.clear();

                listViewItemListeners.itemsSorted(ListView.this);

                if (cleared > 0) {
                    listViewSelectionListeners.selectedRangesChanged(ListView.this,
                        getSelectedRanges());

                    if (selectMode == SelectMode.SINGLE) {
                        listViewSelectionListeners.selectedItemChanged(ListView.this, null);
                    }
                }
            }
        }
    };

    private ListViewListenerList listViewListeners = new ListViewListenerList();
    private ListViewItemListenerList listViewItemListeners = new ListViewItemListenerList();
    private ListViewItemStateListenerList listViewItemStateListeners = new ListViewItemStateListenerList();
    private ListViewSelectionListenerList listViewSelectionListeners = new ListViewSelectionListenerList();
    private ListViewBindingListenerList listViewBindingListeners = new ListViewBindingListenerList();

    private static final ItemRenderer DEFAULT_ITEM_RENDERER = new ListViewItemRenderer();

    /**
     * Creates a list view populated with an empty array list.
     */
    public ListView() {
        this(new ArrayList<>());
    }

    /**
     * Creates a list view populated with the given list data. <p> Note that the
     * default renderer uses (as last option) the toString method on list
     * elements, so override it to return whatever you want to display in the
     * ListView, or implement your own custom renderer.
     *
     * @param listData The data to set.
     * @see ListViewItemRenderer
     */
    public ListView(List<?> listData) {
        setItemRenderer(DEFAULT_ITEM_RENDERER);
        setListData(listData);

        installSkin(ListView.class);
    }

    /**
     * Returns the list data.
     *
     * @return The data currently presented by the list view.
     */
    public List<?> getListData() {
        return this.listData;
    }

    /**
     * Sets the list data.
     *
     * @param listData The data to be presented by the list view.
     */
    @SuppressWarnings("unchecked")
    public void setListData(List<?> listData) {
        if (listData == null) {
            throw new IllegalArgumentException("listData is null.");
        }

        List<?> previousListData = this.listData;

        if (previousListData != listData) {
            int cleared;
            if (previousListData != null) {
                // Clear any existing selection
                cleared = rangeSelection.getLength();
                rangeSelection.clear();
                checkedIndexes.clear();
                mixedIndexes.clear();

                ((List<Object>) previousListData).getListListeners().remove(listDataListener);
            } else {
                cleared = 0;
            }

            ((List<Object>) listData).getListListeners().add(listDataListener);

            // Update the list data and fire change event
            this.listData = listData;
            listViewListeners.listDataChanged(this, previousListData);

            if (cleared > 0) {
                listViewSelectionListeners.selectedRangesChanged(this, getSelectedRanges());

                if (selectMode == SelectMode.SINGLE) {
                    listViewSelectionListeners.selectedItemChanged(this, null);
                }
            }
        }
    }

    /**
     * Sets the list data.
     *
     * @param listData A JSON string (must begin with <tt>[</tt> and end with
     * <tt>]</tt>) denoting the data to be presented by the list view.
     */
    public final void setListData(String listData) {
        if (listData == null) {
            throw new IllegalArgumentException("listData is null.");
        }

        try {
            setListData(JSONSerializer.parseList(listData));
        } catch (SerializationException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    /**
     * Sets the list data.
     *
     * @param listData A URL referring to a JSON file containing the data to be
     * presented by the list view.
     */
    public void setListData(URL listData) {
        if (listData == null) {
            throw new IllegalArgumentException("listData is null.");
        }

        JSONSerializer jsonSerializer = new JSONSerializer();

        try {
            setListData((List<?>) jsonSerializer.readObject(listData.openStream()));
        } catch (SerializationException exception) {
            throw new IllegalArgumentException(exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    @Override
    protected void setSkin(org.apache.pivot.wtk.Skin skin) {
        if (!(skin instanceof ListView.Skin)) {
            throw new IllegalArgumentException("Skin class must implement "
                + ListView.Skin.class.getName());
        }

        super.setSkin(skin);
    }

    /**
     * Returns the item renderer used for items in this list.
     */
    public ItemRenderer getItemRenderer() {
        return itemRenderer;
    }

    /**
     * Sets the item renderer to be used for items in this list.
     *
     * @param itemRenderer The item renderer for the list.
     */
    public void setItemRenderer(ItemRenderer itemRenderer) {
        if (itemRenderer == null) {
            throw new IllegalArgumentException("itemRenderer is null.");
        }

        ItemRenderer previousItemRenderer = this.itemRenderer;

        if (previousItemRenderer != itemRenderer) {
            this.itemRenderer = itemRenderer;
            listViewListeners.itemRendererChanged(this, previousItemRenderer);
        }
    }

    /**
     * Returns the editor used to edit items in this list.
     *
     * @return The item editor, or <tt>null</tt> if no editor is installed.
     */
    public ItemEditor getItemEditor() {
        return itemEditor;
    }

    /**
     * Sets the editor used to edit items in this list.
     *
     * @param itemEditor The item editor for the list.
     */
    public void setItemEditor(ItemEditor itemEditor) {
        ItemEditor previousItemEditor = this.itemEditor;

        if (previousItemEditor != itemEditor) {
            this.itemEditor = itemEditor;
            listViewListeners.itemEditorChanged(this, previousItemEditor);
        }
    }

    /**
     * Returns the currently selected index, even when in multi-select mode.
     *
     * @return The currently selected index.
     */
    public int getSelectedIndex() {
        return getFirstSelectedIndex();
    }

    /**
     * Sets the selection to a single index.
     *
     * @param index The index to select, or <tt>-1</tt> to clear the selection.
     */
    public void setSelectedIndex(int index) {
        if (index == -1) {
            clearSelection();
        } else {
            int listDataLength = listData.getLength();
            if (listDataLength > 0 && index < listDataLength) {
                setSelectedRange(index, index);
            }
        }
    }

    /**
     * Sets the selection to a single range.
     *
     * @param start
     * @param end
     */
    public void setSelectedRange(int start, int end) {
        ArrayList<Span> selectedRanges = new ArrayList<>();
        selectedRanges.add(new Span(start, end));

        setSelectedRanges(selectedRanges);
    }

    /**
     * Returns the currently selected ranges.
     *
     * @return An immutable list containing the currently selected ranges. Note
     * that the returned list is a wrapper around the actual selection, not a
     * copy. Any changes made to the selection state will be reflected in the
     * list, but events will not be fired.
     */
    public ImmutableList<Span> getSelectedRanges() {
        return rangeSelection.getSelectedRanges();
    }

    /**
     * Sets the selection to the given range sequence. Any overlapping or
     * connecting ranges will be consolidated, and the resulting selection will
     * be sorted in ascending order.
     *
     * @param selectedRanges
     * @return The ranges that were actually set.
     */
    public Sequence<Span> setSelectedRanges(Sequence<Span> selectedRanges) {
        if (selectedRanges == null) {
            throw new IllegalArgumentException("selectedRanges is null.");
        }

        // When we're in mode NONE, the only thing we can do is to clear the
        // selection
        if (selectMode == SelectMode.NONE && selectedRanges.getLength() > 0) {
            throw new IllegalArgumentException("Selection is not enabled.");
        }

        // Update the selection
        Sequence<Span> previousSelectedRanges = this.rangeSelection.getSelectedRanges();
        Object previousSelectedItem = (selectMode == SelectMode.SINGLE) ? getSelectedItem() : null;

        RangeSelection listSelection = new RangeSelection();
        for (int i = 0, n = selectedRanges.getLength(); i < n; i++) {
            Span range = selectedRanges.get(i);

            if (range == null) {
                throw new IllegalArgumentException("range is null.");
            }

            if (range.start < 0 || range.end >= listData.getLength()) {
                throw new IndexOutOfBoundsException();
            }

            listSelection.addRange(range.start, range.end);
        }

        this.rangeSelection = listSelection;

        // Notify listeners
        listViewSelectionListeners.selectedRangesChanged(this, previousSelectedRanges);

        if (selectMode == SelectMode.SINGLE) {
            listViewSelectionListeners.selectedItemChanged(this, previousSelectedItem);
        }

        return getSelectedRanges();
    }

    /**
     * Sets the selection to the given range sequence.
     *
     * @param selectedRanges A JSON-formatted string containing the ranges to
     * select.
     * @return The ranges that were actually set.
     * @see #setSelectedRanges(Sequence)
     */
    public final Sequence<Span> setSelectedRanges(String selectedRanges) {
        if (selectedRanges == null) {
            throw new IllegalArgumentException("selectedRanges is null.");
        }

        try {
            setSelectedRanges(parseSelectedRanges(selectedRanges));
        } catch (SerializationException exception) {
            throw new IllegalArgumentException(exception);
        }

        return getSelectedRanges();
    }

    @SuppressWarnings("unchecked")
    private static Sequence<Span> parseSelectedRanges(String json) throws SerializationException {
        ArrayList<Span> selectedRanges = new ArrayList<>();

        List<?> list = JSONSerializer.parseList(json);
        for (Object item : list) {
            Map<String, ?> map = (Map<String, ?>) item;
            selectedRanges.add(new Span(map));
        }

        return selectedRanges;
    }

    /**
     * Returns the first selected index.
     *
     * @return The first selected index, or <tt>-1</tt> if nothing is selected.
     */
    public int getFirstSelectedIndex() {
        return (rangeSelection.getLength() > 0) ? rangeSelection.get(0).start : -1;
    }

    /**
     * Returns the last selected index.
     *
     * @return The last selected index, or <tt>-1</tt> if nothing is selected.
     */
    public int getLastSelectedIndex() {
        return (rangeSelection.getLength() > 0) ? rangeSelection.get(rangeSelection.getLength() - 1).end
            : -1;
    }

    /**
     * Adds a single index to the selection.
     *
     * @param index The index to add.
     * @return <tt>true</tt> if the index was added to the selection;
     * <tt>false</tt>, otherwise.
     */
    public boolean addSelectedIndex(int index) {
        Sequence<Span> addedRanges = addSelectedRange(index, index);
        return (addedRanges.getLength() > 0);
    }

    /**
     * Adds a range of indexes to the selection.
     *
     * @param start The first index in the range.
     * @param end The last index in the range.
     * @return The ranges that were added to the selection.
     */
    public Sequence<Span> addSelectedRange(int start, int end) {
        if (selectMode != SelectMode.MULTI) {
            throw new IllegalStateException("List view is not in multi-select mode.");
        }

        if (start < 0 || end >= listData.getLength()) {
            throw new IndexOutOfBoundsException();
        }

        Sequence<Span> addedRanges = rangeSelection.addRange(start, end);

        int n = addedRanges.getLength();
        for (int i = 0; i < n; i++) {
            Span addedRange = addedRanges.get(i);
            listViewSelectionListeners.selectedRangeAdded(this, addedRange.start, addedRange.end);
        }

        if (n > 0) {
            listViewSelectionListeners.selectedRangesChanged(this, null);
        }

        return addedRanges;
    }

    /**
     * Adds a range of indexes to the selection.
     *
     * @param range The range to add.
     * @return The ranges that were added to the selection.
     */
    public Sequence<Span> addSelectedRange(Span range) {
        if (range == null) {
            throw new IllegalArgumentException("range is null.");
        }

        return addSelectedRange(range.start, range.end);
    }

    /**
     * Removes a single index from the selection.
     *
     * @param index The index to remove.
     * @return <tt>true</tt> if the index was removed from the selection;
     * <tt>false</tt>, otherwise.
     */
    public boolean removeSelectedIndex(int index) {
        Sequence<Span> removedRanges = removeSelectedRange(index, index);
        return (removedRanges.getLength() > 0);
    }

    /**
     * Removes a range of indexes from the selection.
     *
     * @param start The start of the range to remove.
     * @param end The end of the range to remove.
     * @return The ranges that were removed from the selection.
     */
    public Sequence<Span> removeSelectedRange(int start, int end) {
        if (selectMode != SelectMode.MULTI) {
            throw new IllegalStateException("List view is not in multi-select mode.");
        }

        if (start < 0 || end >= listData.getLength()) {
            throw new IndexOutOfBoundsException();
        }

        Sequence<Span> removedRanges = rangeSelection.removeRange(start, end);

        int n = removedRanges.getLength();
        for (int i = 0; i < n; i++) {
            Span removedRange = removedRanges.get(i);
            listViewSelectionListeners.selectedRangeRemoved(this, removedRange.start,
                removedRange.end);
        }

        if (n > 0) {
            listViewSelectionListeners.selectedRangesChanged(this, null);
        }

        return removedRanges;
    }

    /**
     * Removes a range of indexes from the selection.
     *
     * @param range The range to remove.
     * @return The ranges that were removed from the selection.
     */
    public Sequence<Span> removeSelectedRange(Span range) {
        if (range == null) {
            throw new IllegalArgumentException("range is null.");
        }

        return removeSelectedRange(range.start, range.end);
    }

    /**
     * Selects all items in the list.
     */
    public void selectAll() {
        setSelectedRange(0, listData.getLength() - 1);
    }

    /**
     * Clears the selection.
     */
    public void clearSelection() {
        if (rangeSelection.getLength() > 0) {
            setSelectedRanges(new ArrayList<Span>(0));
        }
    }

    /**
     * Returns the selection state of a given index.
     *
     * @param index The index whose selection state is to be tested.
     * @return <tt>true</tt> if the index is selected; <tt>false</tt>, otherwise.
     */
    public boolean isItemSelected(int index) {
        indexBoundsCheck("index", index, 0, listData.getLength() - 1);

        return (rangeSelection.containsIndex(index));
    }

    public Object getSelectedItem() {
        int index = getSelectedIndex();
        Object item = null;

        if (index >= 0) {
            item = listData.get(index);
        }

        return item;
    }

    @SuppressWarnings("unchecked")
    public void setSelectedItem(Object item) {
        setSelectedIndex((item == null) ? -1 : ((List<Object>) listData).indexOf(item));
    }

    public Sequence<?> getSelectedItems() {
        ArrayList<Object> items = new ArrayList<>();

        for (int i = 0, n = rangeSelection.getLength(); i < n; i++) {
            Span range = rangeSelection.get(i);

            for (int index = range.start; index <= range.end; index++) {
                Object item = listData.get(index);
                items.add(item);
            }
        }

        return items;
    }

    @SuppressWarnings("unchecked")
    public void setSelectedItems(Sequence<Object> items) {
        if (items == null) {
            throw new IllegalArgumentException();
        }

        ArrayList<Span> selectedRanges = new ArrayList<>();

        for (int i = 0, n = items.getLength(); i < n; i++) {
            Object item = items.get(i);
            if (item == null) {
                throw new IllegalArgumentException("item is null");
            }

            int index = ((List<Object>) listData).indexOf(item);
            if (index == -1) {
                throw new IllegalArgumentException("\"" + item + "\" is not a valid selection.");
            }

            selectedRanges.add(new Span(index));
        }

        setSelectedRanges(selectedRanges);
    }

    /**
     * Returns the current selection mode.
     */
    public SelectMode getSelectMode() {
        return selectMode;
    }

    /**
     * Sets the selection mode. Clears the selection if the mode has changed
     * (but does not fire a selection change event).
     *
     * @param selectMode The new selection mode.
     */
    public void setSelectMode(SelectMode selectMode) {
        if (selectMode == null) {
            throw new IllegalArgumentException("selectMode is null.");
        }

        SelectMode previousSelectMode = this.selectMode;

        if (previousSelectMode != selectMode) {
            // Clear any current selection
            clearSelection();

            // Update the selection mode
            this.selectMode = selectMode;

            // Fire select mode change event
            listViewListeners.selectModeChanged(this, previousSelectMode);
        }
    }

    /**
     * Returns the current check mode.
     */
    public boolean getCheckmarksEnabled() {
        return checkmarksEnabled;
    }

    /**
     * Enables or disabled checkmarks. Clears the check state if the check mode
     * has changed (but does not fire any check state change events).
     *
     * @param checkmarksEnabled
     */
    public void setCheckmarksEnabled(boolean checkmarksEnabled) {
        if (this.checkmarksEnabled != checkmarksEnabled) {
            // Clear any current check state
            checkedIndexes.clear();
            mixedIndexes.clear();

            // Update the check mode
            this.checkmarksEnabled = checkmarksEnabled;

            // Fire select mode change event
            listViewListeners.checkmarksEnabledChanged(this);
        }
    }

    /**
     * Returns an item's checked state.
     * <p> For a tri-state checkmark, if the {@link #checkmarksMixedAsChecked} flag
     * is set, this method returns <tt>true</tt> if the state is {@link Button$State#MIXED}.
     *
     * @param index
     */
    public boolean isItemChecked(int index) {
        if (allowTriStateCheckmarks && checkmarksMixedAsChecked) {
            if (ArrayList.binarySearch(mixedIndexes, Integer.valueOf(index)) >= 0) {
                return true;
            }
        }
        return (ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index)) >= 0);
    }

    /**
     * Sets an item's checked state.
     *
     * @param index
     * @param checked
     */
    public void setItemChecked(int index, boolean checked) {
        if (!checkmarksEnabled) {
            throw new IllegalStateException("Checkmarks are not enabled.");
        }

        if (allowTriStateCheckmarks) {
            setItemCheckmarkState(index, checked ? Button.State.SELECTED : Button.State.UNSELECTED);
        } else {
            int i = ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index));

            if ((i < 0 && checked) || (i >= 0 && !checked)) {
                if (checked) {
                    checkedIndexes.insert(Integer.valueOf(index), -(i + 1));
                } else {
                    checkedIndexes.remove(i, 1);
                }

                listViewItemStateListeners.itemCheckedChanged(this, index);
            }
        }
    }

    /**
     * Returns the indexes of currently checked items.
     * <p> If the {@link #checkmarksMixedAsChecked} flag is set this method
     * will return all the checked and <tt>MIXED</tt> state items.
     */
    public ImmutableList<Integer> getCheckedIndexes() {
        if (checkmarksMixedAsChecked) {
            ArrayList<Integer> list = new ArrayList<>(checkedIndexes);
            list.addAll(mixedIndexes);
            return new ImmutableList<>(list);
        }
        return new ImmutableList<>(checkedIndexes);
    }

    /**
     * Clears the checked state of all checked items.
     */
    public void clearCheckmarks() {
        List<Integer> checkedIndexesLocal = this.checkedIndexes;
        List<Integer> mixedIndexesLocal = this.mixedIndexes;
        this.checkedIndexes = new ArrayList<>();
        this.mixedIndexes = new ArrayList<>();

        for (Integer index : checkedIndexesLocal) {
            listViewItemStateListeners.itemCheckedChanged(this, index.intValue());
        }
        if (checkmarksMixedAsChecked) {
            for (Integer index : mixedIndexesLocal) {
                listViewItemStateListeners.itemCheckedChanged(this, index.intValue());
            }
        } else {
            for (Integer index : mixedIndexesLocal) {
                listViewItemStateListeners.itemCheckedStateChanged(this, index.intValue());
            }
        }

    }

    /**
     * Tells whether or not an item's checkmark is disabled.
     *
     * @param index The index of the item whose disabled checkmark state is to
     * be tested.
     * @return <tt>true</tt> if the item's checkmark is disabled; <tt>false</tt>
     * otherwise.
     */
    @SuppressWarnings("unchecked")
    public boolean isCheckmarkDisabled(int index) {
        boolean disabled = false;

        if (disabledCheckmarkFilter != null) {
            Object item = listData.get(index);
            disabled = ((Filter<Object>) disabledCheckmarkFilter).include(item);
        }

        return disabled;
    }

    /**
     * Gets the state of all items' checkmarks (for the tri-state case).
     */
    public ImmutableList<Button.State> getCheckmarkStates() {
        List<Button.State> states = new ArrayList<>();
        if (listData != null) {
            // For speed:  initially set all to unselected, then do a simple
            // iteration through the checked and mixed arrays, updating the
            // appropriate entry in the list with the updated state
            for (int index = 0; index < listData.getLength(); index++) {
                states.add(Button.State.UNSELECTED);
            }
            for (Integer checked : checkedIndexes) {
                int index = checked.intValue();
                states.update(index, Button.State.SELECTED);
            }
            for (Integer mixed : mixedIndexes) {
                int index = mixed.intValue();
                states.update(index, Button.State.MIXED);
            }
        }
        return new ImmutableList<>(states);
    }

    /**
     * Gets an item's checkmark state (for tri-state checkmarks).
     * <p> Note: this method returns the real state regardless of the
     * setting of the {@link #checkmarksMixedAsChecked} flag.
     */
    public Button.State getItemCheckmarkState(int index) {
        // Find out where the item is stored currently (if at all)
        int checked = ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index));
        int mixed   = allowTriStateCheckmarks ? ArrayList.binarySearch(mixedIndexes, Integer.valueOf(index)) : -1;

        if (checked < 0 && mixed < 0) {
            return Button.State.UNSELECTED;
        }
        if (checked >= 0) {
            return Button.State.SELECTED;
        }
        return Button.State.MIXED;
    }

    /**
     * Sets an item's checkmark state (for tri-state checkmarks).
     *
     * @param   state
     */
    public void setItemCheckmarkState(int index, Button.State state) {
        if (!checkmarksEnabled) {
            throw new IllegalStateException("Checkmarks are not enabled.");
        }
        if (!allowTriStateCheckmarks) {
            throw new IllegalStateException("Tri-state checkmarks are not enabled.");
        }

        // Find out where the item is stored currently (if at all)
        int checked = ArrayList.binarySearch(checkedIndexes, Integer.valueOf(index));
        int mixed   = ArrayList.binarySearch(mixedIndexes, Integer.valueOf(index));

        // There are six possible transitions here:
        // 1. Unchecked -> Mixed
        // 2. Unchecked -> Checked
        // 3. Mixed -> Unchecked
        // 4. Mixed -> Checked
        // 5. Checked -> Mixed
        // 6. Checked -> Unchecked

        Button.State currentState = Button.State.UNSELECTED;
        if (checked >= 0) {
            currentState = Button.State.SELECTED;
        } else if (mixed >= 0) {
            currentState = Button.State.MIXED;
        }
        if (state != currentState) {
            boolean itemCheckedListener = false;
            boolean itemStateListener = false;

            // Remove it from its current place (if any)
            if (checked >= 0) {
                checkedIndexes.remove(checked, 1);
                itemCheckedListener = true;
            }
            if (mixed >= 0) {
                mixedIndexes.remove(mixed, 1);
                itemStateListener = true;
            }
            // Now put it back in its new place (if necessary)
            switch (state) {
                case MIXED:
                    mixedIndexes.insert(Integer.valueOf(index), -(mixed + 1));
                    itemStateListener = true;
                    break;
                case SELECTED:
                    checkedIndexes.insert(Integer.valueOf(index), -(checked + 1));
                    itemCheckedListener = true;
                    break;
            }

            // Now, notify any listeners necessary (one or two)
            if (itemCheckedListener) {
                listViewItemStateListeners.itemCheckedChanged(this, index);
            }
            if (itemStateListener) {
                listViewItemStateListeners.itemCheckedStateChanged(this, index);
            }
        }
    }

    /**
     * Returns the disabled checkmark filter, which determines which checkboxes
     * are interactive and which are not. Note that this filter only affects
     * user interaction; items may still be checked programatically despite
     * their inclusion in this filter. If this filter is set to <tt>null</tt>,
     * all checkboxes will be interactive. <p> <b>Note:</b> this filter is only
     * relavent if {@link #setCheckmarksEnabled(boolean) checkmarksEnabled} is
     * set to true.
     *
     * @return The disabled checkmark filter, or <tt>null</tt> if no disabled
     * checkmark filter is set
     */
    public Filter<?> getDisabledCheckmarkFilter() {
        return disabledCheckmarkFilter;
    }

    /**
     * Sets the disabled checkmark filter, which determines which checkboxes are
     * interactive and which are not. Note that this filter only affects user
     * interaction; items may still be checked programatically despite their
     * inclusion in this filter. If this filter is set to <tt>null</tt>, all
     * checkboxes will be interactive. <p> <b>Note:</b> this filter is only
     * relavent if {@link #setCheckmarksEnabled(boolean) checkmarksEnabled} is
     * set to true. enabled.
     *
     * @param disabledCheckmarkFilter The disabled checkmark filter, or
     * <tt>null</tt> for no disabled checkmark filter
     */
    public void setDisabledCheckmarkFilter(Filter<?> disabledCheckmarkFilter) {
        Filter<?> previousDisabledCheckmarkFilter = this.disabledCheckmarkFilter;

        if (previousDisabledCheckmarkFilter != disabledCheckmarkFilter) {
            this.disabledCheckmarkFilter = disabledCheckmarkFilter;
            listViewListeners.disabledCheckmarkFilterChanged(this, previousDisabledCheckmarkFilter);
        }
    }

    /**
     * Get the flag saying whether tri-state checkmarks are allowed in this <tt>ListView</tt>.
     * <p> Tri-state checkmarks show checked, unchecked and mixed states.  For a list view with
     * this property enabled, then there are additional methods to set the mixed state, to get
     * the true state of all the items, and to decide if mixed state should be treated as checked
     * or not for all the other "checked" methods.
     */
    public boolean getAllowTriStateCheckmarks() {
        return allowTriStateCheckmarks;
    }

    /**
     * Set the flag saying whether tri-state checkmarks are allowed in this <tt>ListView</tt>.
     * <p> Tri-state checkmarks show checked, unchecked and mixed states.  For a list view with
     * this property enabled, then there are additional methods to set the mixed state, to get
     * the true state of all the items, and to decide if mixed state should be treated as checked
     * or not for all the other "checked" methods.
     * <p> Clears the check state if the setting has changed (but does not fire any check
     * state change events).
     *
     * @param   allow
     */
    public void setAllowTriStateCheckmarks(boolean allow) {
        if (allowTriStateCheckmarks != allow) {
            // Clear any current check state
            checkedIndexes.clear();
            mixedIndexes.clear();

            allowTriStateCheckmarks = allow;

            listViewListeners.checkmarksTriStateChanged(this);
        }
    }

    /**
     * Get the flag saying whether the mixed state of tri-state checkmarks should be treated for
     * all other purposes as "checked" or not.  This setting will also affect what happens when
     * we get mouse clicks on the checkmark.  Set to <tt>false</tt> <code>UNSELECTED</code>
     *  will go to <code>MIXED</code>; while set to <tt>true</tt> <code>UNSELECTED</code> will
     * go to <code>SELECTED</code>.
     */
    public boolean getCheckmarksMixedAsChecked() {
        return checkmarksMixedAsChecked;
    }

    /**
     * Set the flag saying whether the mixed state of the tri-state checkmarks should be treated for
     * all other purposes as "checked" or not.
     *
     * @param mixedAsChecked
     */
    public void setCheckmarksMixedAsChecked(boolean mixedAsChecked) {
        if (checkmarksMixedAsChecked != mixedAsChecked) {
            checkmarksMixedAsChecked = mixedAsChecked;

            listViewListeners.checkmarksMixedAsCheckedChanged(this);
        }
    }

    /**
     * Returns an item's disabled state.
     *
     * @param index The index of the item whose disabled state is to be tested.
     * @return <tt>true</tt> if the item is disabled; <tt>false</tt>, otherwise.
     */
    @SuppressWarnings("unchecked")
    public boolean isItemDisabled(int index) {
        boolean disabled = false;

        if (disabledItemFilter != null) {
            Object item = listData.get(index);
            disabled = ((Filter<Object>) disabledItemFilter).include(item);
        }

        return disabled;
    }

    /**
     * Returns the disabled item filter.
     *
     * @return The disabled item filter, or <tt>null</tt> if no disabled item
     * filter is set.
     */
    public Filter<?> getDisabledItemFilter() {
        return disabledItemFilter;
    }

    /**
     * Sets the disabled item filter.
     *
     * @param disabledItemFilter The disabled item filter, or <tt>null</tt> for
     * no disabled item filter.
     */
    public void setDisabledItemFilter(Filter<?> disabledItemFilter) {
        Filter<?> previousDisabledItemFilter = this.disabledItemFilter;

        if (previousDisabledItemFilter != disabledItemFilter) {
            this.disabledItemFilter = disabledItemFilter;
            listViewListeners.disabledItemFilterChanged(this, previousDisabledItemFilter);
        }
    }

    /**
     * Returns name of the key that is used in context binding.
     *
     * @return The key.
     */
    public String getListDataKey() {
        return listDataKey;
    }

    /**
     * Set the name of the key that is used in context binding.
     *
     * @param listDataKey The key to set.
     */
    public void setListDataKey(String listDataKey) {
        String previousListDataKey = this.listDataKey;

        if (!Utils.stringsAreEqual(previousListDataKey, listDataKey)) {
            this.listDataKey = listDataKey;
            listViewBindingListeners.listDataKeyChanged(this, previousListDataKey);
        }
    }

    public BindType getListDataBindType() {
        return listDataBindType;
    }

    public void setListDataBindType(BindType listDataBindType) {
        if (listDataBindType == null) {
            throw new IllegalArgumentException();
        }

        BindType previousListDataBindType = this.listDataBindType;

        if (previousListDataBindType != listDataBindType) {
            this.listDataBindType = listDataBindType;
            listViewBindingListeners.listDataBindTypeChanged(this, previousListDataBindType);
        }
    }

    public ListDataBindMapping getListDataBindMapping() {
        return listDataBindMapping;
    }

    public void setListDataBindMapping(ListDataBindMapping listDataBindMapping) {
        ListDataBindMapping previousListDataBindMapping = this.listDataBindMapping;

        if (previousListDataBindMapping != listDataBindMapping) {
            this.listDataBindMapping = listDataBindMapping;
            listViewBindingListeners.listDataBindMappingChanged(this, previousListDataBindMapping);
        }
    }

    public String getSelectedItemKey() {
        return selectedItemKey;
    }

    public void setSelectedItemKey(String selectedItemKey) {
        String previousSelectedItemKey = this.selectedItemKey;

        if (!Utils.stringsAreEqual(previousSelectedItemKey, selectedItemKey)) {
            this.selectedItemKey = selectedItemKey;
            listViewBindingListeners.selectedItemKeyChanged(this, previousSelectedItemKey);
        }
    }

    public BindType getSelectedItemBindType() {
        return selectedItemBindType;
    }

    public void setSelectedItemBindType(BindType selectedItemBindType) {
        if (selectedItemBindType == null) {
            throw new IllegalArgumentException();
        }

        BindType previousSelectedItemBindType = this.selectedItemBindType;
        if (previousSelectedItemBindType != selectedItemBindType) {
            this.selectedItemBindType = selectedItemBindType;
            listViewBindingListeners.selectedItemBindTypeChanged(this, previousSelectedItemBindType);
        }
    }

    public ItemBindMapping getSelectedItemBindMapping() {
        return selectedItemBindMapping;
    }

    public void setSelectedItemBindMapping(ItemBindMapping selectedItemBindMapping) {
        ItemBindMapping previousSelectedItemBindMapping = this.selectedItemBindMapping;

        if (previousSelectedItemBindMapping != selectedItemBindMapping) {
            this.selectedItemBindMapping = selectedItemBindMapping;
            listViewBindingListeners.selectedItemBindMappingChanged(this,
                previousSelectedItemBindMapping);
        }
    }

    public String getSelectedItemsKey() {
        return selectedItemsKey;
    }

    public void setSelectedItemsKey(String selectedItemsKey) {
        String previousSelectedItemsKey = this.selectedItemsKey;

        if (!Utils.stringsAreEqual(previousSelectedItemsKey, selectedItemsKey)) {
            this.selectedItemsKey = selectedItemsKey;
            listViewBindingListeners.selectedItemsKeyChanged(this, previousSelectedItemsKey);
        }
    }

    public BindType getSelectedItemsBindType() {
        return selectedItemsBindType;
    }

    public void setSelectedItemsBindType(BindType selectedItemsBindType) {
        if (selectedItemsBindType == null) {
            throw new IllegalArgumentException();
        }

        BindType previousSelectedItemsBindType = this.selectedItemsBindType;
        if (previousSelectedItemsBindType != selectedItemsBindType) {
            this.selectedItemsBindType = selectedItemsBindType;
            listViewBindingListeners.selectedItemsBindTypeChanged(this,
                previousSelectedItemsBindType);
        }
    }

    public ItemBindMapping getSelectedItemsBindMapping() {
        return selectedItemsBindMapping;
    }

    public void setSelectedItemsBindMapping(ItemBindMapping selectedItemsBindMapping) {
        ItemBindMapping previousSelectedItemsBindMapping = this.selectedItemsBindMapping;

        if (previousSelectedItemsBindMapping != selectedItemsBindMapping) {
            this.selectedItemsBindMapping = selectedItemsBindMapping;
            listViewBindingListeners.selectedItemsBindMappingChanged(this,
                previousSelectedItemsBindMapping);
        }
    }

    public String getCheckedItemsKey() {
        return checkedItemsKey;
    }

    public void setCheckedItemsKey(String checkedItemsKey) {
        String previousCheckedItemsKey = this.checkedItemsKey;

        if (!Utils.stringsAreEqual(previousCheckedItemsKey, checkedItemsKey)) {
            this.checkedItemsKey = checkedItemsKey;
            listViewBindingListeners.checkedItemsKeyChanged(this, previousCheckedItemsKey);
        }
    }

    public BindType getCheckedItemsBindType() {
        return checkedItemsBindType;
    }

    public void setCheckedItemsBindType(BindType checkedItemsBindType) {
        if (checkedItemsBindType == null) {
            throw new IllegalArgumentException();
        }

        BindType previousCheckedItemsBindType = this.checkedItemsBindType;
        if (previousCheckedItemsBindType != checkedItemsBindType) {
            this.checkedItemsBindType = checkedItemsBindType;
            listViewBindingListeners.checkedItemsBindTypeChanged(this, previousCheckedItemsBindType);
        }
    }

    public ItemBindMapping getCheckedItemsBindMapping() {
        return checkedItemsBindMapping;
    }

    public void setCheckedItemsBindMapping(ItemBindMapping checkedItemsBindMapping) {
        ItemBindMapping previousCheckedItemsBindMapping = this.checkedItemsBindMapping;

        if (previousCheckedItemsBindMapping != checkedItemsBindMapping) {
            this.checkedItemsBindMapping = checkedItemsBindMapping;
            listViewBindingListeners.checkedItemsBindMappingChanged(this,
                previousCheckedItemsBindMapping);
        }
    }

    public String getItemsStateKey() {
        return itemsStateKey;
    }

    public void setItemsStateKey(String itemsStateKey) {
        String previousItemsStateKey = this.itemsStateKey;

        if (!Utils.stringsAreEqual(previousItemsStateKey, itemsStateKey)) {
            this.itemsStateKey = itemsStateKey;
            listViewBindingListeners.itemsStateKeyChanged(this, previousItemsStateKey);
        }
    }

    public BindType getItemsStateBindType() {
        return itemsStateBindType;
    }

    public void setItemsStateBindType(BindType itemsStateBindType) {
        if (itemsStateBindType == null) {
            throw new IllegalArgumentException("Bind type must not be null");
        }

        BindType previousItemsStateBindType = this.itemsStateBindType;
        if (previousItemsStateBindType != itemsStateBindType) {
            this.itemsStateBindType = itemsStateBindType;
            listViewBindingListeners.itemsStateBindTypeChanged(this, previousItemsStateBindType);
        }
    }

    public ItemStateBindMapping getItemsStateBindMapping() {
        return itemsStateBindMapping;
    }

    public void setItemsStateBindMapping(ItemStateBindMapping itemsStateBindMapping) {
        ItemStateBindMapping previousItemsStateBindMapping = this.itemsStateBindMapping;

        if (previousItemsStateBindMapping != itemsStateBindMapping) {
            this.itemsStateBindMapping = itemsStateBindMapping;
            listViewBindingListeners.itemsStateBindMappingChanged(this,
                previousItemsStateBindMapping);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void load(Object context) {
        // Bind to list data
        if (listDataKey != null && listDataBindType != BindType.STORE
            && JSON.containsKey(context, listDataKey)) {
            Object value = JSON.get(context, listDataKey);

            List<?> listDataLocal;
            if (listDataBindMapping == null) {
                listDataLocal = (List<?>) value;
            } else {
                listDataLocal = listDataBindMapping.toListData(value);
            }

            setListData(listDataLocal);
        }

        switch (selectMode) {
            case SINGLE: {
                // Bind using selected item key
                if (selectedItemKey != null && selectedItemBindType != BindType.STORE
                    && JSON.containsKey(context, selectedItemKey)) {
                    Object item = JSON.get(context, selectedItemKey);

                    int index;
                    if (selectedItemBindMapping == null) {
                        index = ((List<Object>) listData).indexOf(item);
                    } else {
                        index = selectedItemBindMapping.indexOf(listData, item);
                    }

                    setSelectedIndex(index);
                }

                break;
            }

            case MULTI: {
                // Bind using selected items key
                if (selectedItemsKey != null && selectedItemsBindType != BindType.STORE
                    && JSON.containsKey(context, selectedItemsKey)) {
                    Sequence<Object> items = (Sequence<Object>) JSON.get(context, selectedItemsKey);

                    clearSelection();

                    for (int i = 0, n = items.getLength(); i < n; i++) {
                        Object item = items.get(i);

                        int index;
                        if (selectedItemsBindMapping == null) {
                            index = ((List<Object>) listData).indexOf(item);
                        } else {
                            index = selectedItemsBindMapping.indexOf(listData, item);
                        }

                        if (index != -1) {
                            addSelectedIndex(index);
                        }
                    }
                }

                break;
            }

            case NONE: {
                break;
            }

            default: {
                break;
            }
        }

        if (checkmarksEnabled) {
            if (allowTriStateCheckmarks) {
                if (itemsStateKey != null && JSON.containsKey(context, itemsStateKey)
                    && itemsStateBindType != BindType.STORE) {
                    Sequence<Object> items = (Sequence<Object>) JSON.get(context, itemsStateKey);

                    clearCheckmarks();

                    for (int i = 0, n = items.getLength(); i < n; i++) {
                        Object item = items.get(i);

                        int index;
                        if (itemsStateBindMapping == null) {
                            index = ((List<Object>) listData).indexOf(item);
                        } else {
                            index = itemsStateBindMapping.indexOf(listData, item);
                        }

                        if (index != -1) {
                            Button.State state = Button.State.UNSELECTED;
                            if (itemsStateBindMapping == null) {
                                state = itemsStateBindMapping.getState(item);
                            } else {
                                state = Button.State.SELECTED;
                            }
                            setItemCheckmarkState(index, state);
                        }
                    }
                }
            }
            if (checkedItemsKey != null && JSON.containsKey(context, checkedItemsKey)
                && checkedItemsBindType != BindType.STORE) {
                Sequence<Object> items = (Sequence<Object>) JSON.get(context, checkedItemsKey);

                clearCheckmarks();

                for (int i = 0, n = items.getLength(); i < n; i++) {
                    Object item = items.get(i);

                    int index;
                    if (checkedItemsBindMapping == null) {
                        index = ((List<Object>) listData).indexOf(item);
                    } else {
                        index = checkedItemsBindMapping.indexOf(listData, item);
                    }

                    if (index != -1) {
                        setItemChecked(index, true);
                    }
                }
            }
        }
    }

    @Override
    public void store(Object context) {
        // Bind to list data
        if (listDataKey != null && listDataBindType != BindType.LOAD) {
            Object value;
            if (listDataBindMapping == null) {
                value = listData;
            } else {
                value = listDataBindMapping.valueOf(listData);
            }

            JSON.put(context, listDataKey, value);
        }

        switch (selectMode) {
            case SINGLE: {
                // Bind using selected item key
                if (selectedItemKey != null && selectedItemBindType != BindType.LOAD) {
                    Object item;

                    int selectedIndexLocal = getSelectedIndex();
                    if (selectedItemBindMapping == null) {
                        if (selectedIndexLocal == -1) {
                            item = null;
                        } else {
                            item = listData.get(selectedIndexLocal);
                        }
                    } else {
                        item = selectedItemBindMapping.get(listData, selectedIndexLocal);
                    }

                    JSON.put(context, selectedItemKey, item);
                }

                break;
            }

            case MULTI: {
                // Bind using selected items key
                if (selectedItemsKey != null && selectedItemsBindType != BindType.LOAD) {
                    ArrayList<Object> items = new ArrayList<>();

                    Sequence<Span> selectedRanges = getSelectedRanges();
                    for (int i = 0, n = selectedRanges.getLength(); i < n; i++) {
                        Span range = selectedRanges.get(i);

                        for (int index = range.start; index <= range.end; index++) {
                            Object item;
                            if (selectedItemsBindMapping == null) {
                                item = listData.get(index);
                            } else {
                                item = selectedItemsBindMapping.get(listData, index);
                            }

                            items.add(item);
                        }
                    }

                    JSON.put(context, selectedItemsKey, items);
                }

                break;
            }

            case NONE: {
                break;
            }

            default: {
                break;
            }
        }

        if (checkmarksEnabled) {
            if (allowTriStateCheckmarks) {
                if (itemsStateKey != null && JSON.containsKey(context, itemsStateKey)
                    && itemsStateBindType != BindType.LOAD) {
                    ArrayList<Object> items = new ArrayList<>();

                    for (int i = 0, n = mixedIndexes.getLength(); i < n; i++) {
                        Integer index = mixedIndexes.get(i);

                        Object item;
                        if (itemsStateBindMapping == null) {
                            item = listData.get(index.intValue());
                        } else {
                            item = itemsStateBindMapping.get(listData, index.intValue());
                        }

                        if (itemsStateBindMapping == null) {
                            // TODO: ?? what to do here?  we need to set the MIXED state for the item
                        } else {
                            itemsStateBindMapping.setState(item, Button.State.MIXED);
                        }

                        items.add(item);
                    }

                    // TODO: what about the mixedAsChecked flag?  Does it make a difference here or not?

                    for (int i = 0, n = checkedIndexes.getLength(); i < n; i++) {
                        Integer index = checkedIndexes.get(i);

                        Object item;
                        if (itemsStateBindMapping == null) {
                            item = listData.get(index.intValue());
                        } else {
                            item = itemsStateBindMapping.get(listData, index.intValue());
                        }

                        if (itemsStateBindMapping == null) {
                            // TODO: ?? what to do here?  we need to set the SELECTED state for the item
                        } else {
                            itemsStateBindMapping.setState(item, Button.State.SELECTED);
                        }

                        items.add(item);
                    }

                    JSON.put(context, itemsStateKey, items);
                }
            }
            if (checkedItemsKey != null && JSON.containsKey(context, checkedItemsKey)
                && checkedItemsBindType != BindType.LOAD) {
                ArrayList<Object> items = new ArrayList<>();

                for (int i = 0, n = checkedIndexes.getLength(); i < n; i++) {
                    Integer index = checkedIndexes.get(i);

                    Object item;
                    if (checkedItemsBindMapping == null) {
                        item = listData.get(index.intValue());
                    } else {
                        item = checkedItemsBindMapping.get(listData, index.intValue());
                    }

                    items.add(item);
                }

                JSON.put(context, checkedItemsKey, items);
            }
        }
    }

    @Override
    public void clear() {
        if (listDataKey != null) {
            setListData(new ArrayList<>());
        }

        if (selectedItemKey != null || selectedItemsKey != null) {
            setSelectedItem(null);
        }

        if (checkedItemsKey != null) {
            clearCheckmarks();
        }
    }

    /**
     * Returns the index of the item at a given location.
     *
     * @param y The y-coordinate of the item to identify.
     * @return The item index, or <tt>-1</tt> if there is no item at the given
     * y-coordinate.
     */
    public int getItemAt(int y) {
        ListView.Skin listViewSkin = (ListView.Skin) getSkin();
        return listViewSkin.getItemAt(y);
    }

    /**
     * Returns the bounding area of a given item.
     *
     * @param index The item index.
     * @return The bounding area of the item.
     */
    public Bounds getItemBounds(int index) {
        ListView.Skin listViewSkin = (ListView.Skin) getSkin();
        return listViewSkin.getItemBounds(index);
    }

    /**
     * Returns the item indent.
     *
     * @return The horizontal space preceding items in the list.
     */
    public int getItemIndent() {
        ListView.Skin listViewSkin = (ListView.Skin) getSkin();
        return listViewSkin.getItemIndent();
    }

    /**
     * Returns the list view listener list.
     */
    public ListenerList<ListViewListener> getListViewListeners() {
        return listViewListeners;
    }

    /**
     * Returns the list view item listener list.
     */
    public ListenerList<ListViewItemListener> getListViewItemListeners() {
        return listViewItemListeners;
    }

    /**
     * Returns the list view item state listener list.
     */
    public ListenerList<ListViewItemStateListener> getListViewItemStateListeners() {
        return listViewItemStateListeners;
    }

    /**
     * Returns the list view selection detail listener list.
     */
    public ListenerList<ListViewSelectionListener> getListViewSelectionListeners() {
        return listViewSelectionListeners;
    }

    public ListenerList<ListViewBindingListener> getListViewBindingListeners() {
        return listViewBindingListeners;
    }
}
