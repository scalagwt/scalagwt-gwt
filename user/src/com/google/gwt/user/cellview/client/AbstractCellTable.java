/*
 * Copyright 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.user.cellview.client;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.IconCellDecorator;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.builder.shared.DivBuilder;
import com.google.gwt.dom.builder.shared.HtmlTableSectionBuilder;
import com.google.gwt.dom.builder.shared.TableCellBuilder;
import com.google.gwt.dom.builder.shared.TableRowBuilder;
import com.google.gwt.dom.builder.shared.TableSectionBuilder;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.OutlineStyle;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HasHorizontalAlignment.HorizontalAlignmentConstant;
import com.google.gwt.user.client.ui.HasVerticalAlignment.VerticalAlignmentConstant;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for tabular views that supports paging and columns.
 * 
 * <p>
 * <h3>Columns</h3> The {@link Column} class defines the {@link Cell} used to
 * render a column. Implement {@link Column#getValue(Object)} to retrieve the
 * field value from the row object that will be rendered in the {@link Cell}.
 * </p>
 * 
 * <p>
 * <h3>Headers and Footers</h3> A {@link Header} can be placed at the top
 * (header) or bottom (footer) of the {@link AbstractCellTable}. You can specify
 * a header as text using {@link #addColumn(Column, String)}, or you can create
 * a custom {@link Header} that can change with the value of the cells, such as
 * a column total. The {@link Header} will be rendered every time the row data
 * changes or the table is redrawn. If you pass the same header instance (==)
 * into adjacent columns, the header will span the columns.
 * </p>
 * 
 * @param <T> the data type of each row
 */
public abstract class AbstractCellTable<T> extends AbstractHasData<T> {

  /**
   * Default implementation of a keyboard navigation handler for tables that
   * supports navigation between cells.
   * 
   * @param <T> the data type of each row
   */
  public static class CellTableKeyboardSelectionHandler<T> extends
      DefaultKeyboardSelectionHandler<T> {

    private final AbstractCellTable<T> table;

    /**
     * Construct a new keyboard selection handler for the specified table.
     * 
     * @param table the display being handled
     */
    public CellTableKeyboardSelectionHandler(AbstractCellTable<T> table) {
      super(table);
      this.table = table;
    }

    @Override
    public AbstractCellTable<T> getDisplay() {
      return table;
    }

    @Override
    public void onCellPreview(CellPreviewEvent<T> event) {
      NativeEvent nativeEvent = event.getNativeEvent();
      String eventType = event.getNativeEvent().getType();
      if ("keydown".equals(eventType) && !event.isCellEditing()) {
        /*
         * Handle keyboard navigation, unless the cell is being edited. If the
         * cell is being edited, we do not want to change rows.
         * 
         * Prevent default on navigation events to prevent default scrollbar
         * behavior.
         */
        int oldRow = table.getKeyboardSelectedRow();
        int oldColumn = table.getKeyboardSelectedColumn();
        boolean isRtl = LocaleInfo.getCurrentLocale().isRTL();
        int keyCodeLineEnd = isRtl ? KeyCodes.KEY_LEFT : KeyCodes.KEY_RIGHT;
        int keyCodeLineStart = isRtl ? KeyCodes.KEY_RIGHT : KeyCodes.KEY_LEFT;
        int keyCode = nativeEvent.getKeyCode();
        if (keyCode == keyCodeLineEnd) {
          int nextColumn = findInteractiveColumn(oldColumn, false);
          if (nextColumn <= oldColumn) {
            // Wrap to the next row.
            table.setKeyboardSelectedRow(oldRow + 1);
            if (table.getKeyboardSelectedRow() != oldRow) {
              // If the row didn't change, we are at the end of the table.
              table.setKeyboardSelectedColumn(nextColumn);
              handledEvent(event);
              return;
            }
          } else {
            table.setKeyboardSelectedColumn(nextColumn);
            handledEvent(event);
            return;
          }
        } else if (keyCode == keyCodeLineStart) {
          int prevColumn = findInteractiveColumn(oldColumn, true);
          if (prevColumn >= oldColumn) {
            // Wrap to the previous row.
            table.setKeyboardSelectedRow(oldRow - 1);
            if (table.getKeyboardSelectedRow() != oldRow) {
              // If the row didn't change, we are at the start of the table.
              table.setKeyboardSelectedColumn(prevColumn);
              handledEvent(event);
              return;
            }
          } else {
            table.setKeyboardSelectedColumn(prevColumn);
            handledEvent(event);
            return;
          }
        }
      } else if ("click".equals(eventType) || "focus".equals(eventType)) {
        /*
         * Move keyboard focus to the clicked column, even if the cell is being
         * edited. Unlike key events, we aren't moving the currently selected
         * row, just updating it based on where the user clicked.
         * 
         * Since the user clicked, allow focus to go to a non-interactive
         * column.
         */
        int col = event.getColumn();
        int relRow = event.getIndex() - table.getPageStart();
        int subrow = event.getContext().getSubIndex();
        if ((table.getKeyboardSelectedColumn() != col)
            || (table.getKeyboardSelectedRow() != relRow)
            || (table.getKeyboardSelectedSubRow() != subrow)) {
          boolean stealFocus = false;
          if ("click".equals(eventType)) {
            // If a natively focusable element was just clicked, then do not
            // steal focus.
            Element target = Element.as(event.getNativeEvent().getEventTarget());
            stealFocus = !CellBasedWidgetImpl.get().isFocusable(target);
          }

          // Update the row and subrow.
          table.setKeyboardSelectedRow(relRow, subrow, stealFocus);

          // Update the column index.
          table.setKeyboardSelectedColumn(col, stealFocus);
        }

        // Do not cancel the event as the click may have occurred on a Cell.
        return;
      }

      // Let the parent class handle the event.
      super.onCellPreview(event);
    }

    /**
     * Find and return the index of the next interactive column. If no column is
     * interactive, 0 is returned. If the start index is the only interactive
     * column, it is returned.
     * 
     * @param start the start index, exclusive unless it is the only option
     * @param reverse true to do a reverse search
     * @return the interactive column index, or 0 if not interactive
     */
    private int findInteractiveColumn(int start, boolean reverse) {
      if (!table.isInteractive) {
        return 0;
      } else if (reverse) {
        for (int i = start - 1; i >= 0; i--) {
          if (isColumnInteractive(table.getColumn(i))) {
            return i;
          }
        }
        // Wrap to the end.
        for (int i = table.getColumnCount() - 1; i >= start; i--) {
          if (isColumnInteractive(table.getColumn(i))) {
            return i;
          }
        }
      } else {
        for (int i = start + 1; i < table.getColumnCount(); i++) {
          if (isColumnInteractive(table.getColumn(i))) {
            return i;
          }
        }
        // Wrap to the start.
        for (int i = 0; i <= start; i++) {
          if (isColumnInteractive(table.getColumn(i))) {
            return i;
          }
        }
      }
      return 0;
    }
  }

  /**
   * Default cell table builder that renders row values into a grid of columns.
   * 
   * @param <T> the data type of the rows.
   */
  public static class DefaultCellTableBuilder<T> extends AbstractCellTableBuilder<T> {

    private final String evenRowStyle;
    private final String oddRowStyle;
    private final String selectedRowStyle;
    private final String cellStyle;
    private final String evenCellStyle;
    private final String oddCellStyle;
    private final String firstColumnStyle;
    private final String lastColumnStyle;
    private final String selectedCellStyle;

    public DefaultCellTableBuilder(AbstractCellTable<T> cellTable) {
      super(cellTable);
      // Cache styles for faster access.
      Style style = cellTable.getResources().style();
      evenRowStyle = style.evenRow();
      oddRowStyle = style.oddRow();
      selectedRowStyle = " " + style.selectedRow();
      cellStyle = style.cell();
      evenCellStyle = " " + style.evenRowCell();
      oddCellStyle = " " + style.oddRowCell();
      firstColumnStyle = " " + style.firstColumn();
      lastColumnStyle = " " + style.lastColumn();
      selectedCellStyle = " " + style.selectedRowCell();
    }
    
    @Override
    public void buildRowImpl(T rowValue, int absRowIndex) {

      // Calculate the row styles.
      SelectionModel<? super T> selectionModel = cellTable.getSelectionModel();
      boolean isSelected =
          (selectionModel == null || rowValue == null) ? false : selectionModel
              .isSelected(rowValue);
      boolean isEven = absRowIndex % 2 == 0;
      StringBuilder trClasses = new StringBuilder(isEven ? evenRowStyle : oddRowStyle);
      if (isSelected) {
        trClasses.append(selectedRowStyle);
      }

      // Add custom row styles.
      RowStyles<T> rowStyles = cellTable.getRowStyles();
      if (rowStyles != null) {
        String extraRowStyles = rowStyles.getStyleNames(rowValue, absRowIndex);
        if (extraRowStyles != null) {
          trClasses.append(" ").append(extraRowStyles);
        }
      }

      // Build the row.
      TableRowBuilder tr = startRow();
      tr.className(trClasses.toString());

      // Build the columns.
      int columnCount = cellTable.getColumnCount();
      for (int curColumn = 0; curColumn < columnCount; curColumn++) {
        Column<T, ?> column = cellTable.getColumn(curColumn);
        // Create the cell styles.
        StringBuilder tdClasses = new StringBuilder(cellStyle);
        tdClasses.append(isEven ? evenCellStyle : oddCellStyle);
        if (curColumn == 0) {
          tdClasses.append(firstColumnStyle);
        }
        if (isSelected) {
          tdClasses.append(selectedCellStyle);
        }
        // The first and last column could be the same column.
        if (curColumn == columnCount - 1) {
          tdClasses.append(lastColumnStyle);
        }

        // Add class names specific to the cell.
        Context context = new Context(absRowIndex, curColumn, cellTable.getValueKey(rowValue));
        String cellStyles = column.getCellStyleNames(context, rowValue);
        if (cellStyles != null) {
          tdClasses.append(" " + cellStyles);
        }

        // Builder the cell.
        HorizontalAlignmentConstant hAlign = column.getHorizontalAlignment();
        VerticalAlignmentConstant vAlign = column.getVerticalAlignment();
        TableCellBuilder td = tr.startTD();
        td.className(tdClasses.toString());
        if (hAlign != null) {
          td.align(hAlign.getTextAlignString());
        }
        if (vAlign != null) {
          td.vAlign(vAlign.getVerticalAlignString());
        }

        // Add the inner div.
        DivBuilder div = td.startDiv();
        div.style().outlineStyle(OutlineStyle.NONE).endStyle();

        // Render the cell into the div.
        renderCell(div, context, column, rowValue);

        // End the cell.
        div.endDiv();
        td.endTD();
      }

      // End the row.
      tr.endTR();
    }
  }

  /**
   * A ClientBundle that provides images for this widget.
   */
  public interface Resources {
    /**
     * Icon used when a column is sorted in ascending order.
     */
    ImageResource sortAscending();

    /**
     * Icon used when a column is sorted in descending order.
     */
    ImageResource sortDescending();

    /**
     * The styles used in this widget.
     */
    Style style();
  }

  /**
   * Styles used by this widget.
   */
  public interface Style {
    /**
     * Applied to every cell.
     */
    String cell();

    /**
     * Applied to even rows.
     */
    String evenRow();

    /**
     * Applied to cells in even rows.
     */
    String evenRowCell();

    /**
     * Applied to the first column.
     */
    String firstColumn();

    /**
     * Applied to the first column footers.
     */
    String firstColumnFooter();

    /**
     * Applied to the first column headers.
     */
    String firstColumnHeader();

    /**
     * Applied to footers cells.
     */
    String footer();

    /**
     * Applied to headers cells.
     */
    String header();

    /**
     * Applied to the hovered row.
     */
    String hoveredRow();

    /**
     * Applied to the cells in the hovered row.
     */
    String hoveredRowCell();

    /**
     * Applied to the keyboard selected cell.
     */
    String keyboardSelectedCell();

    /**
     * Applied to the keyboard selected row.
     */
    String keyboardSelectedRow();

    /**
     * Applied to the cells in the keyboard selected row.
     */
    String keyboardSelectedRowCell();

    /**
     * Applied to the last column.
     */
    String lastColumn();

    /**
     * Applied to the last column footers.
     */
    String lastColumnFooter();

    /**
     * Applied to the last column headers.
     */
    String lastColumnHeader();

    /**
     * Applied to odd rows.
     */
    String oddRow();

    /**
     * Applied to cells in odd rows.
     */
    String oddRowCell();

    /**
     * Applied to selected rows.
     */
    String selectedRow();

    /**
     * Applied to cells in selected rows.
     */
    String selectedRowCell();

    /**
     * Applied to header cells that are sortable.
     */
    String sortableHeader();

    /**
     * Applied to header cells that are sorted in ascending order.
     */
    String sortedHeaderAscending();

    /**
     * Applied to header cells that are sorted in descending order.
     */
    String sortedHeaderDescending();

    /**
     * Applied to the table.
     */
    String widget();
  }

  interface Template extends SafeHtmlTemplates {
    @SafeHtmlTemplates.Template("<div style=\"outline:none;\">{0}</div>")
    SafeHtml div(SafeHtml contents);

    @SafeHtmlTemplates.Template("<div class=\"{0}\"></div>")
    SafeHtml loading(String loading);

    @SafeHtmlTemplates.Template("<table><tbody>{0}</tbody></table>")
    SafeHtml tbody(SafeHtml rowHtml);

    @SafeHtmlTemplates.Template("<td class=\"{0}\">{1}</td>")
    SafeHtml td(String classes, SafeHtml contents);

    @SafeHtmlTemplates.Template("<td class=\"{0}\" align=\"{1}\" valign=\"{2}\">{3}</td>")
    SafeHtml tdBothAlign(String classes, String hAlign, String vAlign, SafeHtml contents);

    @SafeHtmlTemplates.Template("<td class=\"{0}\" align=\"{1}\">{2}</td>")
    SafeHtml tdHorizontalAlign(String classes, String hAlign, SafeHtml contents);

    @SafeHtmlTemplates.Template("<td class=\"{0}\" valign=\"{1}\">{2}</td>")
    SafeHtml tdVerticalAlign(String classes, String vAlign, SafeHtml contents);

    @SafeHtmlTemplates.Template("<table><tfoot>{0}</tfoot></table>")
    SafeHtml tfoot(SafeHtml rowHtml);

    @SafeHtmlTemplates.Template("<th colspan=\"{0}\" class=\"{1}\">{2}</th>")
    SafeHtml th(int colspan, String classes, SafeHtml contents);

    @SafeHtmlTemplates.Template("<table><thead>{0}</thead></table>")
    SafeHtml thead(SafeHtml rowHtml);

    @SafeHtmlTemplates.Template("<tr onclick=\"\" class=\"{0}\">{1}</tr>")
    SafeHtml tr(String classes, SafeHtml contents);
  }

  /**
   * Implementation of {@link AbstractCellTable}.
   */
  private static class Impl {

    private final com.google.gwt.user.client.Element tmpElem = Document.get().createDivElement()
        .cast();

    /**
     * Convert the rowHtml into Elements wrapped by the specified table section.
     * 
     * @param table the {@link AbstractCellTable}
     * @param sectionTag the table section tag
     * @param rowHtml the Html for the rows
     * @return the section element
     */
    public TableSectionElement convertToSectionElement(AbstractCellTable<?> table,
        String sectionTag, SafeHtml rowHtml) {
      // Attach an event listener so we can catch synchronous load events from
      // cached images.
      DOM.setEventListener(tmpElem, table);

      /*
       * Render the rows into a table.
       * 
       * IE doesn't support innerHtml on a TableSection or Table element, so we
       * generate the entire table. We do the same for all browsers to avoid any
       * future bugs, since setting innerHTML on a table section seems brittle.
       */
      sectionTag = sectionTag.toLowerCase();
      if ("tbody".equals(sectionTag)) {
        tmpElem.setInnerHTML(template.tbody(rowHtml).asString());
      } else if ("thead".equals(sectionTag)) {
        tmpElem.setInnerHTML(template.thead(rowHtml).asString());
      } else if ("tfoot".equals(sectionTag)) {
        tmpElem.setInnerHTML(template.tfoot(rowHtml).asString());
      } else {
        throw new IllegalArgumentException("Invalid table section tag: " + sectionTag);
      }
      TableElement tableElem = tmpElem.getFirstChildElement().cast();

      // Detach the event listener.
      DOM.setEventListener(tmpElem, null);

      // Get the section out of the table.
      if ("tbody".equals(sectionTag)) {
        return tableElem.getTBodies().getItem(0);
      } else if ("thead".equals(sectionTag)) {
        return tableElem.getTHead();
      } else if ("tfoot".equals(sectionTag)) {
        return tableElem.getTFoot();
      } else {
        throw new IllegalArgumentException("Invalid table section tag: " + sectionTag);
      }
    }

    /**
     * Render a table section in the table.
     * 
     * @param table the {@link AbstractCellTable}
     * @param section the {@link TableSectionElement} to replace
     * @param html the html of a table section element containing the rows
     */
    public final void replaceAllRows(AbstractCellTable<?> table, TableSectionElement section,
        SafeHtml html) {
      // If the widget is not attached, attach an event listener so we can catch
      // synchronous load events from cached images.
      if (!table.isAttached()) {
        DOM.setEventListener(table.getElement(), table);
      }

      // Remove the section from the tbody.
      Element parent = section.getParentElement();
      Element nextSection = section.getNextSiblingElement();
      detachSectionElement(section);

      // Render the html.
      replaceAllRowsImpl(table, section, html);

      /*
       * Reattach the section. If next section is null, the section will be
       * appended instead.
       */
      reattachSectionElement(parent, section, nextSection);

      // Detach the event listener.
      if (!table.isAttached()) {
        DOM.setEventListener(table.getElement(), null);
      }
    }

    /**
     * Replace a set of row values with newly rendered values.
     * 
     * This method does not necessarily perform a one to one replacement. Some
     * row values may be rendered as multiple row elements, while others are
     * rendered as only one row element.
     * 
     * @param table the {@link AbstractCellTable}
     * @param section the {@link TableSectionElement} to replace
     * @param html the html of a table section element containing the rows
     * @param startIndex the start index to replace
     * @param childCount the number of row values to replace
     */
    public final void replaceChildren(AbstractCellTable<?> table, TableSectionElement section,
        SafeHtml html, int startIndex, int childCount) {
      // If the widget is not attached, attach an event listener so we can catch
      // synchronous load events from cached images.
      if (!table.isAttached()) {
        DOM.setEventListener(table.getElement(), table);
      }

      // Remove the section from the tbody.
      Element parent = section.getParentElement();
      Element nextSection = section.getNextSiblingElement();
      detachSectionElement(section);

      // Remove all children in the range.
      final int absEndIndex = table.getPageStart() + startIndex + childCount;
      boolean done = false;
      Element insertBefore = table.getChildElement(startIndex);
      if (table.legacyRenderRowValues) {
        int count = 0;
        while (insertBefore != null && count < childCount) {
          Element next = insertBefore.getNextSiblingElement();
          section.removeChild(insertBefore);
          insertBefore = next;
          count++;
        }
      } else {
        while (insertBefore != null && table.tableBuilder.getRowValueIndex(
            insertBefore.<TableRowElement> cast()) < absEndIndex) {
          Element next = insertBefore.getNextSiblingElement();
          section.removeChild(insertBefore);
          insertBefore = next;
        }
      }

      // Add new child elements.
      TableSectionElement newSection = convertToSectionElement(table, section.getTagName(), html);
      Element newChild = newSection.getFirstChildElement();
      while (newChild != null) {
        Element next = newChild.getNextSiblingElement();
        section.insertBefore(newChild, insertBefore);
        newChild = next;
      }

      /*
       * Reattach the section. If next section is null, the section will be
       * appended instead.
       */
      reattachSectionElement(parent, section, nextSection);

      // Detach the event listener.
      if (!table.isAttached()) {
        DOM.setEventListener(table.getElement(), null);
      }
    }

    /**
     * Detach a table section element from its parent.
     * 
     * @param section the element to detach
     */
    protected void detachSectionElement(TableSectionElement section) {
      section.removeFromParent();
    }

    /**
     * Reattach a table section element from its parent.
     * 
     * @param parent the parent element
     * @param section the element to reattach
     * @param nextSection the next section
     */
    protected void reattachSectionElement(Element parent, TableSectionElement section,
        Element nextSection) {
      parent.insertBefore(section, nextSection);
    }

    /**
     * Render a table section in the table.
     * 
     * @param table the {@link AbstractCellTable}
     * @param section the {@link TableSectionElement} to replace
     * @param html the html of a table section element containing the rows
     */
    protected void replaceAllRowsImpl(AbstractCellTable<?> table, TableSectionElement section,
        SafeHtml html) {
      section.setInnerHTML(html.asString());
    }
  }

  /**
   * Implementation of {@link CellTable} used by Firefox.
   */
  @SuppressWarnings("unused")
  private static class ImplMozilla extends Impl {
    /**
     * Firefox 3.6 and earlier convert td elements to divs if the tbody is
     * removed from the table element.
     */
    @Override
    protected void detachSectionElement(TableSectionElement section) {
      if (isGecko192OrBefore()) {
        return;
      }
      super.detachSectionElement(section);
    }

    @Override
    protected void reattachSectionElement(Element parent, TableSectionElement section,
        Element nextSection) {
      if (isGecko192OrBefore()) {
        return;
      }
      super.reattachSectionElement(parent, section, nextSection);
    }

    /**
     * Return true if using Gecko 1.9.2 (Firefox 3.6) or earlier.
     */
    private native boolean isGecko192OrBefore() /*-{
      return @com.google.gwt.dom.client.DOMImplMozilla::isGecko192OrBefore()();
    }-*/;
  }

  /**
   * Implementation of {@link AbstractCellTable} used by IE.
   */
  @SuppressWarnings("unused")
  private static class ImplTrident extends Impl {

    /**
     * Detaching a tbody in IE throws an error.
     */
    @Override
    protected void detachSectionElement(TableSectionElement section) {
      return;
    }

    @Override
    protected void reattachSectionElement(Element parent, TableSectionElement section,
        Element nextSection) {
      return;
    }

    /**
     * IE doesn't support innerHTML on tbody, nor does it support removing or
     * replacing a tbody. The only solution is to remove and replace the rows
     * themselves.
     */
    @Override
    protected void replaceAllRowsImpl(AbstractCellTable<?> table, TableSectionElement section,
        SafeHtml html) {
      // Remove all children.
      Element child = section.getFirstChildElement();
      while (child != null) {
        Element next = child.getNextSiblingElement();
        section.removeChild(child);
        child = next;
      }

      // Add new child elements.
      TableSectionElement newSection = convertToSectionElement(table, section.getTagName(), html);
      child = newSection.getFirstChildElement();
      while (child != null) {
        Element next = child.getNextSiblingElement();
        section.appendChild(child);
        child = next;
      }
    }
  }

  /**

   * The table specific {@link Impl}.
   */
  private static Impl TABLE_IMPL;

  private static Template template;

  /**
   * Check if a column consumes events.
   */
  private static boolean isColumnInteractive(HasCell<?, ?> column) {
    Set<String> consumedEvents = column.getCell().getConsumedEvents();
    return consumedEvents != null && consumedEvents.size() > 0;
  }

  private boolean cellIsEditing;
  private final List<Column<T, ?>> columns = new ArrayList<Column<T, ?>>();
  private final Map<Column<T, ?>, String> columnWidths = new HashMap<Column<T, ?>, String>();
  private boolean columnWidthsDirty;
  private final Map<Integer, String> columnWidthsByIndex = new HashMap<Integer, String>();

  /**
   * Indicates that at least one column depends on selection.
   */
  private boolean dependsOnSelection;

  private Widget emptyTableWidget;
  private boolean footerRefreshDisabled;
  private final List<Header<?>> footers = new ArrayList<Header<?>>();

  /**
   * Indicates that at least one column handles selection.
   */
  private boolean handlesSelection;

  private boolean headerRefreshDisabled;
  private final List<Header<?>> headers = new ArrayList<Header<?>>();

  /**
   * Indicates that either the headers or footers are dirty, and both should be
   * refreshed the next time the table is redrawn.
   */
  private boolean headersDirty;

  private TableRowElement hoveringRow;

  /**
   * Indicates that at least one column is interactive.
   */
  private boolean isInteractive;

  private int keyboardSelectedColumn = 0;
  private int keyboardSelectedSubrow = 0;
  private int lastKeyboardSelectedSubrow = 0;
  private Widget loadingIndicator;
  private boolean legacyRenderRowValues = true;
  private final Resources resources;
  private RowStyles<T> rowStyles;
  private IconCellDecorator<SafeHtml> sortAscDecorator;
  private IconCellDecorator<SafeHtml> sortDescDecorator;
  private final ColumnSortList sortList = new ColumnSortList(new ColumnSortList.Delegate() {
    @Override
    public void onModification() {
      if (!updatingSortList) {
        createHeaders(false);
      }
    }
  });
  private final Style style;
  private CellTableBuilder<T> tableBuilder;
  private boolean updatingSortList;

  /**
   * Constructs a table with the given page size, the specified {@link Style},
   * and the given key provider.
   * 
   * @param elem the parent {@link Element}
   * @param pageSize the page size
   * @param resources the resources to apply to the widget
   * @param keyProvider an instance of ProvidesKey<T>, or null if the record
   *          object should act as its own key
   */
  public AbstractCellTable(Element elem, final int pageSize, Resources resources,
      ProvidesKey<T> keyProvider) {
    super(elem, pageSize, keyProvider);
    this.resources = resources;
    this.style = resources.style();
    init();
  }

  /**
   * Constructs a table with the given page size, the specified {@link Style},
   * and the given key provider.
   * 
   * @param widget the parent widget
   * @param pageSize the page size
   * @param resources the resources to apply to the widget
   * @param keyProvider an instance of ProvidesKey<T>, or null if the record
   *          object should act as its own key
   */
  public AbstractCellTable(Widget widget, final int pageSize, Resources resources,
      ProvidesKey<T> keyProvider) {
    super(widget, pageSize, keyProvider);
    this.resources = resources;
    this.style = resources.style();
    init();
  }

  /**
   * Adds a column to the end of the table.
   * 
   * @param col the column to be added
   */
  public void addColumn(Column<T, ?> col) {
    insertColumn(getColumnCount(), col);
  }

  /**
   * Adds a column to the end of the table with an associated header.
   * 
   * @param col the column to be added
   * @param header the associated {@link Header}
   */
  public void addColumn(Column<T, ?> col, Header<?> header) {
    insertColumn(getColumnCount(), col, header);
  }

  /**
   * Adds a column to the end of the table with an associated header and footer.
   * 
   * @param col the column to be added
   * @param header the associated {@link Header}
   * @param footer the associated footer (as a {@link Header} object)
   */
  public void addColumn(Column<T, ?> col, Header<?> header, Header<?> footer) {
    insertColumn(getColumnCount(), col, header, footer);
  }

  /**
   * Adds a column to the end of the table with an associated String header.
   * 
   * @param col the column to be added
   * @param headerString the associated header text, as a String
   */
  public void addColumn(Column<T, ?> col, String headerString) {
    insertColumn(getColumnCount(), col, headerString);
  }

  /**
   * Adds a column to the end of the table with an associated {@link SafeHtml}
   * header.
   * 
   * @param col the column to be added
   * @param headerHtml the associated header text, as safe HTML
   */
  public void addColumn(Column<T, ?> col, SafeHtml headerHtml) {
    insertColumn(getColumnCount(), col, headerHtml);
  }

  /**
   * Adds a column to the end of the table with an associated String header and
   * footer.
   * 
   * @param col the column to be added
   * @param headerString the associated header text, as a String
   * @param footerString the associated footer text, as a String
   */
  public void addColumn(Column<T, ?> col, String headerString, String footerString) {
    insertColumn(getColumnCount(), col, headerString, footerString);
  }

  /**
   * Adds a column to the end of the table with an associated {@link SafeHtml}
   * header and footer.
   * 
   * @param col the column to be added
   * @param headerHtml the associated header text, as safe HTML
   * @param footerHtml the associated footer text, as safe HTML
   */
  public void addColumn(Column<T, ?> col, SafeHtml headerHtml, SafeHtml footerHtml) {
    insertColumn(getColumnCount(), col, headerHtml, footerHtml);
  }

  /**
   * Add a handler to handle {@link ColumnSortEvent}s.
   * 
   * @param handler the {@link ColumnSortEvent.Handler} to add
   * @return a {@link HandlerRegistration} to remove the handler
   */
  public HandlerRegistration addColumnSortHandler(ColumnSortEvent.Handler handler) {
    return addHandler(handler, ColumnSortEvent.getType());
  }

  /**
   * Add a style name to the <code>col</code> element at the specified index,
   * creating it if necessary.
   * 
   * @param index the column index
   * @param styleName the style name to add
   */
  public abstract void addColumnStyleName(int index, String styleName);

  /**
   * Clear the width of the specified {@link Column}.
   * 
   * @param column the column
   */
  public void clearColumnWidth(Column<T, ?> column) {
    columnWidths.remove(column);
    updateColumnWidthImpl(column, null);
  }

  /**
   * Clear the width of the specified {@link Column}.
   * 
   * @param column the column index
   */
  public void clearColumnWidth(Integer column) {
    columnWidthsByIndex.remove(column);
    // TODO(jlabanca): Compare to realColumnCount when headerBuilder lands.
    if (column < getColumnCount()) {
      doSetColumnWidth(column, null);
    }
  }

  /**
   * Flush all pending changes to the table and render immediately.
   * 
   * <p>
   * Modifications to the table, such as adding columns or setting data, are not
   * rendered immediately. Instead, changes are coalesced at the end of the
   * current event loop to avoid rendering the table multiple times. Use this
   * method to force the table to render all pending modifications immediately.
   * </p>
   */
  public void flush() {
    getPresenter().flush();
  }
  
  /**
   * Get the column at the specified index.
   * 
   * @param col the index of the column to retrieve
   * @return the {@link Column} at the index
   */
  public Column<T, ?> getColumn(int col) {
    checkColumnBounds(col);
    return columns.get(col);
  }

  /**
   * Get the number of columns in the table.
   * 
   * @return the column count
   */
  public int getColumnCount() {
    return columns.size();
  }

  /**
   * Get the index of the specified column.
   * 
   * @param column the column to search for
   * @return the index of the column, or -1 if not found
   */
  public int getColumnIndex(Column<T, ?> column) {
    return columns.indexOf(column);
  }

  /**
   * Get the {@link ColumnSortList} that specifies which columns are sorted.
   * Modifications to the {@link ColumnSortList} will be reflected in the table
   * header.
   * 
   * <p>
   * Note that the implementation may redraw the headers on every modification
   * to the {@link ColumnSortList}.
   * </p>
   * 
   * @return the {@link ColumnSortList}
   */
  public ColumnSortList getColumnSortList() {
    return sortList;
  }

  /**
   * Get the width of a {@link Column}.
   * 
   * @param column the column
   * @return the width of the column, or null if not set
   * @see #setColumnWidth(Column, double, Unit)
   */
  public String getColumnWidth(Column<T, ?> column) {
    return columnWidths.get(column);
  }

  /**
   * Get the widget displayed when the table has no rows.
   * 
   * @return the empty table widget
   */
  public Widget getEmptyTableWidget() {
    return emptyTableWidget;
  }

  /**
   * Get the index of the column that is currently selected via the keyboard.
   * 
   * @return the currently selected column, or -1 if none selected
   */
  public int getKeyboardSelectedColumn() {
    return KeyboardSelectionPolicy.DISABLED == getKeyboardSelectionPolicy() ? -1
        : keyboardSelectedColumn;
  }

  /**
   * Get the index of the sub row that is currently selected via the keyboard.
   * If the row value maps to one rendered row element, the subrow is 0.
   * 
   * @return the currently selected subrow, or -1 if none selected
   */
  public int getKeyboardSelectedSubRow() {
    return KeyboardSelectionPolicy.DISABLED == getKeyboardSelectionPolicy() ? -1
        : keyboardSelectedSubrow;
  }

  /**
   * Get the widget displayed when the data is loading.
   * 
   * @return the loading indicator
   */
  public Widget getLoadingIndicator() {
    return loadingIndicator;
  }

  /**
   * Get the resources used by this table.
   */
  public Resources getResources() {
    return resources;
  }

  /**
   * Get the {@link TableRowElement} for the specified row. If the row element
   * has not been created, null is returned.
   * 
   * @param row the row index
   * @return the row element, or null if it doesn't exists
   * @throws IndexOutOfBoundsException if the row index is outside of the
   *           current page
   */
  public TableRowElement getRowElement(int row) {
    flush();
    return getChildElement(row);
  }

  /**
   * Gets the object used to determine how a row is styled.
   * 
   * @return the {@link RowStyles} object if set, null if not
   */
  public RowStyles<T> getRowStyles() {
    return this.rowStyles;
  }

  /**
   * Inserts a column into the table at the specified index.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col) {
    insertColumn(beforeIndex, col, (Header<?>) null, (Header<?>) null);
  }

  /**
   * Inserts a column into the table at the specified index with an associated
   * header.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   * @param header the associated {@link Header}
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col, Header<?> header) {
    insertColumn(beforeIndex, col, header, null);
  }

  /**
   * Inserts a column into the table at the specified index with an associated
   * header and footer.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   * @param header the associated {@link Header}
   * @param footer the associated footer (as a {@link Header} object)
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col, Header<?> header, Header<?> footer) {
    // Allow insert at the end.
    if (beforeIndex != getColumnCount()) {
      checkColumnBounds(beforeIndex);
    }

    headers.add(beforeIndex, header);
    footers.add(beforeIndex, footer);
    columns.add(beforeIndex, col);

    // Increment the keyboard selected column.
    if (beforeIndex <= keyboardSelectedColumn) {
      keyboardSelectedColumn = Math.min(keyboardSelectedColumn + 1, columns.size() - 1);
    }

    // Move the keyboard selected column if the current column is not
    // interactive.
    if (isColumnInteractive(col)
        && ((keyboardSelectedColumn >= columns.size()) || !isColumnInteractive(columns
            .get(keyboardSelectedColumn)))) {
      keyboardSelectedColumn = beforeIndex;
    }

    // Sink events used by the new column.
    Set<String> consumedEvents = new HashSet<String>();
    {
      Set<String> cellEvents = col.getCell().getConsumedEvents();
      if (cellEvents != null) {
        consumedEvents.addAll(cellEvents);
      }
    }
    if (header != null) {
      Set<String> headerEvents = header.getCell().getConsumedEvents();
      if (headerEvents != null) {
        consumedEvents.addAll(headerEvents);
      }
    }
    if (footer != null) {
      Set<String> footerEvents = footer.getCell().getConsumedEvents();
      if (footerEvents != null) {
        consumedEvents.addAll(footerEvents);
      }
    }
    CellBasedWidgetImpl.get().sinkEvents(this, consumedEvents);

    headersDirty = true;
    refreshColumnsAndRedraw();
  }

  /**
   * Inserts a column into the table at the specified index with an associated
   * String header.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   * @param headerString the associated header text, as a String
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col, String headerString) {
    insertColumn(beforeIndex, col, new TextHeader(headerString), null);
  }

  /**
   * Inserts a column into the table at the specified index with an associated
   * {@link SafeHtml} header.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   * @param headerHtml the associated header text, as safe HTML
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col, SafeHtml headerHtml) {
    insertColumn(beforeIndex, col, new SafeHtmlHeader(headerHtml), null);
  }

  /**
   * Inserts a column into the table at the specified index with an associated
   * String header and footer.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   * @param headerString the associated header text, as a String
   * @param footerString the associated footer text, as a String
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col, String headerString,
      String footerString) {
    insertColumn(beforeIndex, col, new TextHeader(headerString), new TextHeader(footerString));
  }

  /**
   * Inserts a column into the table at the specified index with an associated
   * {@link SafeHtml} header and footer.
   * 
   * @param beforeIndex the index to insert the column
   * @param col the column to be added
   * @param headerHtml the associated header text, as safe HTML
   * @param footerHtml the associated footer text, as safe HTML
   */
  public void insertColumn(int beforeIndex, Column<T, ?> col, SafeHtml headerHtml,
      SafeHtml footerHtml) {
    insertColumn(beforeIndex, col, new SafeHtmlHeader(headerHtml), new SafeHtmlHeader(footerHtml));
  }

  /**
   * Check if auto footer refresh is enabled or disabled
   * 
   * @return true if disabled, false if enabled
   * @see #setAutoFooterRefreshDisabled(boolean)
   */
  public boolean isAutoFooterRefreshDisabled() {
    return footerRefreshDisabled;
  }

  /**
   * Check if auto header refresh is enabled or disabled
   * 
   * @return true if disabled, false if enabled
   * @see #setAutoHeaderRefreshDisabled(boolean)
   */
  public boolean isAutoHeaderRefreshDisabled() {
    return headerRefreshDisabled;
  }

  /**
   * Redraw the table's footers. The footers will be re-rendered synchronously.
   */
  public void redrawFooters() {
    createHeaders(true);
  }

  /**
   * Redraw the table's headers. The headers will be re-rendered synchronously.
   */
  public void redrawHeaders() {
    createHeaders(false);
  }

  /**
   * Remove a column.
   * 
   * @param col the column to remove
   */
  public void removeColumn(Column<T, ?> col) {
    int index = columns.indexOf(col);
    if (index < 0) {
      throw new IllegalArgumentException("The specified column is not part of this table.");
    }
    removeColumn(index);
  }

  /**
   * Remove a column.
   * 
   * @param index the column index
   */
  public void removeColumn(int index) {
    if (index < 0 || index >= columns.size()) {
      throw new IndexOutOfBoundsException("The specified column index is out of bounds.");
    }
    columns.remove(index);
    headers.remove(index);
    footers.remove(index);

    // Decrement the keyboard selected column.
    if (index <= keyboardSelectedColumn && keyboardSelectedColumn > 0) {
      keyboardSelectedColumn--;
    }

    // Redraw the table asynchronously.
    headersDirty = true;
    refreshColumnsAndRedraw();

    // We don't unsink events because other handlers or user code may have sunk
    // them intentionally.
  }

  /**
   * Remove a style from the <code>col</code> element at the specified index.
   * 
   * @param index the column index
   * @param styleName the style name to remove
   */
  public abstract void removeColumnStyleName(int index, String styleName);

  /**
   * Enable or disable auto footer refresh when row data is changed. By default,
   * footers are refreshed every time the row data changes in case the headers
   * depend on the current row data. If the headers do not depend on the current
   * row data, you can disable this feature to improve performance.
   * 
   * <p>
   * Note that headers will still refresh when columns are added or removed,
   * regardless of whether or not this feature is enabled.
   * </p>
   */
  public void setAutoFooterRefreshDisabled(boolean disabled) {
    this.footerRefreshDisabled = disabled;
  }

  /**
   * Enable or disable auto header refresh when row data is changed. By default,
   * headers are refreshed every time the row data changes in case the footers
   * depend on the current row data. If the footers do not depend on the current
   * row data, you can disable this feature to improve performance.
   * 
   * <p>
   * Note that footers will still refresh when columns are added or removed,
   * regardless of whether or not this feature is enabled.
   * </p>
   */
  public void setAutoHeaderRefreshDisabled(boolean disabled) {
    this.headerRefreshDisabled = disabled;
  }

  /**
   * Set the width of a {@link Column}. The width will persist with the column
   * and takes precedence of any width set via
   * {@link #setColumnWidth(int, String)}.
   * 
   * @param column the column
   * @param width the width of the column
   */
  public void setColumnWidth(Column<T, ?> column, String width) {
    columnWidths.put(column, width);
    updateColumnWidthImpl(column, width);
  }

  /**
   * Set the width of a {@link Column}. The width will persist with the column
   * and takes precedence of any width set via
   * {@link #setColumnWidth(int, double, Unit)}.
   * 
   * @param column the column
   * @param width the width of the column
   * @param unit the {@link Unit} of measurement
   */
  public void setColumnWidth(Column<T, ?> column, double width, Unit unit) {
    setColumnWidth(column, width + unit.getType());
  }

  /**
   * Set the width of a {@link Column}.
   * 
   * @param column the column
   * @param width the width of the column
   * @param unit the {@link Unit} of measurement
   */
  public void setColumnWidth(int column, double width, Unit unit) {
    setColumnWidth(column, width + unit.getType());
  }

  /**
   * Set the width of a {@link Column}.
   * 
   * @param column the column
   * @param width the width of the column
   */
  public void setColumnWidth(int column, String width) {
    columnWidthsByIndex.put(column, width);
    // TODO(jlabanca): Compare to realColumnCount when headerBuilder lands.
    if (column < getColumnCount()) {
      doSetColumnWidth(column, width);
    }
  }

  /**
   * Set the widget to display when the table has no rows.
   * 
   * @param widget the empty table widget, or null to disable
   */
  public void setEmptyTableWidget(Widget widget) {
    this.emptyTableWidget = widget;
  }

  /**
   * Set the keyboard selected column index.
   * 
   * <p>
   * If keyboard selection is disabled, this method does nothing.
   * </p>
   * 
   * <p>
   * If the keyboard selected column is greater than the number of columns in
   * the keyboard selected row, the last column in the row is selected, but the
   * column index is remembered.
   * </p>
   * 
   * @param column the column index, greater than or equal to zero
   */
  public final void setKeyboardSelectedColumn(int column) {
    setKeyboardSelectedColumn(column, true);
  }

  /**
   * Set the keyboard selected column index and optionally focus on the new
   * cell.
   * 
   * @param column the column index, greater than or equal to zero
   * @param stealFocus true to focus on the new column
   * @see #setKeyboardSelectedColumn(int)
   */
  public void setKeyboardSelectedColumn(int column, boolean stealFocus) {
    assert column >= 0 : "Column must be zero or greater";
    if (KeyboardSelectionPolicy.DISABLED == getKeyboardSelectionPolicy()) {
      return;
    }

    this.keyboardSelectedColumn = column;

    // Reselect the row to move the selected column.
    setKeyboardSelectedRow(getKeyboardSelectedRow(), keyboardSelectedSubrow, stealFocus);
  }

  @Override
  public void setKeyboardSelectedRow(int row, boolean stealFocus) {
    setKeyboardSelectedRow(row, 0, stealFocus);
  }

  /**
   * Set the keyboard selected row and subrow, optionally focus on the new row.
   * 
   * @param row the row index relative to the page start
   * @param subrow the row index of the child row
   * @param stealFocus true to focus on the new row
   * @see #setKeyboardSelectedRow(int)
   */
  public void setKeyboardSelectedRow(int row, int subrow, boolean stealFocus) {
    this.keyboardSelectedSubrow = subrow;
    super.setKeyboardSelectedRow(row, stealFocus);
  }

  /**
   * Set the widget to display when the data is loading.
   * 
   * @param widget the loading indicator, or null to disable
   */
  public void setLoadingIndicator(Widget widget) {
    loadingIndicator = widget;
  }

  /**
   * Sets the object used to determine how a row is styled; the change will take
   * effect the next time that the table is rendered.
   * 
   * @param rowStyles a {@link RowStyles} object
   */
  public void setRowStyles(RowStyles<T> rowStyles) {
    this.rowStyles = rowStyles;
  }

  /**
   * Specify the {@link CellTableBuilder} that will be used to render the row
   * values into the table.
   */
  public void setTableBuilder(CellTableBuilder<T> tableBuilder) {
    assert tableBuilder != null : "tableBuilder cannot be null";
    this.tableBuilder = tableBuilder;
    redraw();
  }

  @Override
  protected Element convertToElements(SafeHtml html) {
    return TABLE_IMPL.convertToSectionElement(AbstractCellTable.this, "tbody", html);
  }

  @Override
  protected boolean dependsOnSelection() {
    return dependsOnSelection;
  }

  /**
   * Set the width of a column.
   * 
   * @param column the column index
   * @param width the width, or null to clear the width
   */
  protected abstract void doSetColumnWidth(int column, String width);

  /**
   * Show or hide a header section.
   * 
   * @param isFooter true for the footer, false for the header
   * @param isVisible true to show, false to hide
   */
  protected abstract void doSetHeaderVisible(boolean isFooter, boolean isVisible);

  @Override
  protected Element getChildContainer() {
    return getTableBodyElement();
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * The row element may not be the same as the TR element at the specified
   * index if some row values are rendered with additional rows.
   * </p>
   * 
   * @param row the row index, relative to the page start
   * @return the row element, or null if it doesn't exists
   * @throws IndexOutOfBoundsException if the row index is outside of the
   *           current page
   */
  @Override
  protected TableRowElement getChildElement(int row) {
    return getSubRowElement(row + getPageStart(), 0);
  }

  @Override
  protected Element getKeyboardSelectedElement() {
    return getKeyboardSelectedElement(getKeyboardSelectedTableCellElement());
  }

  /**
   * Get the tbody element that contains the render row values.
   */
  protected abstract TableSectionElement getTableBodyElement();

  /**
   * Get the tfoot element that contains the footers.
   */
  protected abstract TableSectionElement getTableFootElement();

  /**
   * Get the thead element that contains theh eaders.
   */
  protected abstract TableSectionElement getTableHeadElement();

  @Override
  protected boolean isKeyboardNavigationSuppressed() {
    return cellIsEditing;
  }

  @Override
  protected void onBlur() {
    TableCellElement td = getKeyboardSelectedTableCellElement();
    if (td != null) {
      TableRowElement tr = td.getParentElement().cast();
      td.removeClassName(style.keyboardSelectedCell());
      setRowStyleName(tr, style.keyboardSelectedRow(), style.keyboardSelectedRowCell(), false);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void onBrowserEvent2(Event event) {
    // Get the event target.
    EventTarget eventTarget = event.getEventTarget();
    if (!Element.is(eventTarget)) {
      return;
    }
    final Element target = event.getEventTarget().cast();

    // Find the cell where the event occurred.
    TableSectionElement tbody = getTableBodyElement();
    TableSectionElement tfoot = getTableFootElement();
    TableSectionElement thead = getTableHeadElement();
    TableSectionElement targetTableSection = null;
    TableCellElement targetTableCell = null;
    Element cellParent = null;
    {
      Element maybeTableCell = null;
      Element cur = target;
      while (cur != null && targetTableSection == null) {
        /*
         * Found the table section. Return the most recent cell element that we
         * discovered.
         */
        if (cur == tbody || cur == tfoot || cur == thead) {
          targetTableSection = cur.cast(); // We found the table section.
          if (maybeTableCell != null) {
            targetTableCell = maybeTableCell.cast();
            break;
          }
        }

        // Look for a table cell.
        String tagName = cur.getTagName();
        if (TableCellElement.TAG_TD.equalsIgnoreCase(tagName)
            || TableCellElement.TAG_TH.equalsIgnoreCase(tagName)) {
          /*
           * Found a table cell, but we can't return yet because it may be part
           * of a sub table within the a CellTable cell.
           */
          maybeTableCell = cur;
        }

        // Look for the most immediate cell parent if not already found.
        boolean isColumn = tableBuilder.isColumn(cur);
        if (cellParent == null && isColumn) {
          cellParent = cur;
        }

        // Iterate.
        cur = cur.getParentElement();
      }
    }
    if (targetTableCell == null) {
      return;
    }

    // Support the legacy mode where the div inside of the TD is the cell
    // parent.
    if (legacyRenderRowValues) {
      cellParent = targetTableCell.getFirstChildElement();
    }

    /*
     * Forward the event to the associated header, footer, or column.
     */
    TableRowElement targetTableRow = targetTableCell.getParentElement().cast();
    String eventType = event.getType();
    boolean isClick = "click".equals(eventType);
    int col = targetTableCell.getCellIndex();
    if (targetTableSection == thead) {
      Header<?> header = headers.get(col);
      if (header != null) {
        // Fire the event to the header.
        if (cellConsumesEventType(header.getCell(), eventType)) {
          Context context = new Context(0, col, header.getKey());
          header.onBrowserEvent(context, targetTableCell, event);
        }

        // Sort the header.
        if (isClick) {
          // TODO(jlabanca): Get visible col when custom headers are supported.
          Column<T, ?> column = col < columns.size() ? columns.get(col) : null;
          if (column != null && column.isSortable()) {
            /*
             * Force the headers to refresh the next time data is pushed so we
             * update the sort icon in the header.
             */
            headersDirty = true;
            updatingSortList = true;
            sortList.push(column);
            updatingSortList = false;
            ColumnSortEvent.fire(this, sortList);
          }
        }
      }
    } else if (targetTableSection == tfoot) {
      Header<?> footer = footers.get(col);
      if (footer != null && cellConsumesEventType(footer.getCell(), eventType)) {
        Context context = new Context(0, col, footer.getKey());
        footer.onBrowserEvent(context, targetTableCell, event);
      }
    } else if (targetTableSection == tbody) {
      /*
       * Get the row index of the data value. This may not correspond to the DOM
       * row index if the user specifies multiple table rows per row object.
       */
      int absRow = tableBuilder.getRowValueIndex(targetTableRow);
      int relRow = absRow - getPageStart();
      int subrow = tableBuilder.getSubrowValueIndex(targetTableRow);
      if ("mouseover".equals(eventType)) {
        // Unstyle the old row if it is still part of the table.
        if (hoveringRow != null && getTableBodyElement().isOrHasChild(hoveringRow)) {
          setRowStyleName(hoveringRow, style.hoveredRow(), style.hoveredRowCell(), false);
        }
        hoveringRow = targetTableRow;
        setRowStyleName(hoveringRow, style.hoveredRow(), style.hoveredRowCell(), true);
      } else if ("mouseout".equals(eventType) && hoveringRow != null) {
        setRowStyleName(hoveringRow, style.hoveredRow(), style.hoveredRowCell(), false);
        hoveringRow = null;
      }

      // If the event causes us to page, then the physical index will be out
      // of bounds of the underlying data.
      if (!isRowWithinBounds(relRow)) {
        return;
      }

      /*
       * Fire a preview event. The preview event is fired even if the TD does
       * not contain a cell so the selection handler and keyboard handler have a
       * chance to act.
       */
      boolean isSelectionHandled =
          handlesSelection
              || KeyboardSelectionPolicy.BOUND_TO_SELECTION == getKeyboardSelectionPolicy();
      T value = getVisibleItem(relRow);
      Context context = new Context(absRow, col, getValueKey(value), subrow);
      CellPreviewEvent<T> previewEvent =
          CellPreviewEvent.fire(this, event, this, context, value, cellIsEditing,
              isSelectionHandled);

      // Pass the event to the cell.
      if (cellParent != null && !previewEvent.isCanceled()) {
        HasCell<T, ?> column;
        if (legacyRenderRowValues) {
          column = columns.get(col);
        } else {
          column = tableBuilder.getColumn(context, value, cellParent);
        }
        if (column != null) {
          fireEventToCell(event, eventType, cellParent, value, context, column);
        }
      }
    }
  }

  @Override
  protected void onFocus() {
    TableCellElement td = getKeyboardSelectedTableCellElement();
    if (td != null) {
      TableRowElement tr = td.getParentElement().cast();
      td.addClassName(style.keyboardSelectedCell());
      setRowStyleName(tr, style.keyboardSelectedRow(), style.keyboardSelectedRowCell(), true);
    }
  }

  protected void refreshColumnWidths() {
    // TODO(jlabanca): Set size without looking at column count when custom
    // headers added?
    int columnCount = getColumnCount();
    for (int i = 0; i < columnCount; i++) {
      Column<T, ?> column = columns.get(i);
      String width = columnWidths.get(column);
      if (width == null) {
        width = columnWidthsByIndex.get(i);
      }
      doSetColumnWidth(i, width);
    }
  }

  /**
   * Throws an {@link UnsupportedOperationException}.
   * 
   * @deprecated as of GWT 2.5, use a {@link TableCellBuilder} to customize the
   *             table structure instead
   * @see #renderRowValuesLegacy(SafeHtmlBuilder, List, int, SelectionModel)
   */
  @Override
  @Deprecated
  protected void renderRowValues(SafeHtmlBuilder sb, List<T> values, int start,
      SelectionModel<? super T> selectionModel) {
    legacyRenderRowValues = false;
    throw new UnsupportedOperationException();
  }

  /**
   * Render all row values into the specified {@link SafeHtmlBuilder}.
   * 
   * <p>
   * This method is here for legacy reasons, to support subclasses that call
   * {@link #renderRowValues(SafeHtmlBuilder, List, int, SelectionModel)}.
   * </p>
   * 
   * @param sb the {@link SafeHtmlBuilder} to render into
   * @param values the row values
   * @param start the absolute start index of the values
   * @param selectionModel the {@link SelectionModel}
   * @deprecated as of GWT 2.5, use a {@link TableCellBuilder} to customize the
   *             table structure instead
   */
  @Deprecated
  protected final void renderRowValuesLegacy(SafeHtmlBuilder sb, List<T> values, int start,
      SelectionModel<? super T> selectionModel) {
    int keyboardSelectedRow = getKeyboardSelectedRow() + getPageStart();
    String evenRowStyle = style.evenRow();
    String oddRowStyle = style.oddRow();
    String cellStyle = style.cell();
    String evenCellStyle = " " + style.evenRowCell();
    String oddCellStyle = " " + style.oddRowCell();
    String firstColumnStyle = " " + style.firstColumn();
    String lastColumnStyle = " " + style.lastColumn();
    String selectedRowStyle = " " + style.selectedRow();
    String selectedCellStyle = " " + style.selectedRowCell();
    int columnCount = columns.size();
    int length = values.size();
    int end = start + length;
    for (int i = start; i < end; i++) {
      T value = values.get(i - start);
      boolean isSelected =
          (selectionModel == null || value == null) ? false : selectionModel.isSelected(value);
      boolean isEven = i % 2 == 0;
      String trClasses = isEven ? evenRowStyle : oddRowStyle;
      if (isSelected) {
        trClasses += selectedRowStyle;
      }

      if (rowStyles != null) {
        String extraRowStyles = rowStyles.getStyleNames(value, i);
        if (extraRowStyles != null) {
          trClasses += " ";
          trClasses += extraRowStyles;
        }
      }

      SafeHtmlBuilder trBuilder = new SafeHtmlBuilder();
      int curColumn = 0;
      for (Column<T, ?> column : columns) {
        String tdClasses = cellStyle;
        tdClasses += isEven ? evenCellStyle : oddCellStyle;
        if (curColumn == 0) {
          tdClasses += firstColumnStyle;
        }
        if (isSelected) {
          tdClasses += selectedCellStyle;
        }
        // The first and last column could be the same column.
        if (curColumn == columnCount - 1) {
          tdClasses += lastColumnStyle;
        }

        // Add class names specific to the cell.
        Context context = new Context(i, curColumn, getValueKey(value));
        String cellStyles = column.getCellStyleNames(context, value);
        if (cellStyles != null) {
          tdClasses += " " + cellStyles;
        }

        SafeHtmlBuilder cellBuilder = new SafeHtmlBuilder();
        if (value != null) {
          column.render(context, value, cellBuilder);
        }

        // Build the contents.
        SafeHtml contents = SafeHtmlUtils.EMPTY_SAFE_HTML;
        contents = template.div(cellBuilder.toSafeHtml());

        // Build the cell.
        HorizontalAlignmentConstant hAlign = column.getHorizontalAlignment();
        VerticalAlignmentConstant vAlign = column.getVerticalAlignment();
        if (hAlign != null && vAlign != null) {
          trBuilder.append(template.tdBothAlign(tdClasses, hAlign.getTextAlignString(), vAlign
              .getVerticalAlignString(), contents));
        } else if (hAlign != null) {
          trBuilder.append(template.tdHorizontalAlign(tdClasses, hAlign.getTextAlignString(),
              contents));
        } else if (vAlign != null) {
          trBuilder.append(template.tdVerticalAlign(tdClasses, vAlign.getVerticalAlignString(),
              contents));
        } else {
          trBuilder.append(template.td(tdClasses, contents));
        }

        curColumn++;
      }

      sb.append(template.tr(trClasses, trBuilder.toSafeHtml()));
    }
  }

  @Override
  protected void replaceAllChildren(List<T> values, SafeHtml html) {
    refreshHeadersAndColumnsImpl();

    /*
     * If html is not null, then the user overrode renderRowValues() and
     * rendered directly into a SafeHtmlBuilder. The legacy method is deprecated
     * but still supported.
     */
    if (html == null) {
      html = buildRowValues(values, getPageStart(), true);
    }

    TABLE_IMPL.replaceAllRows(this, getTableBodyElement(), CellBasedWidgetImpl.get().processHtml(
        html));
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void replaceChildren(List<T> values, int start, SafeHtml html) {
    refreshHeadersAndColumnsImpl();

    /*
     * If html is not null, then the user override renderRowValues() and
     * rendered directly into a SafeHtmlBuilder. The legacy method is deprecated
     * but still supported.
     */
    if (html == null) {
      html = buildRowValues(values, getPageStart() + start, false);
    }

    TABLE_IMPL.replaceChildren(this, getTableBodyElement(), CellBasedWidgetImpl.get().processHtml(
        html), start, values.size());
  }

  @Override
  protected boolean resetFocusOnCell() {
    Element elem = getKeyboardSelectedElement();
    if (elem == null) {
      // There is no selected element.
      return false;
    }

    int row = getKeyboardSelectedRow();
    int col = getKeyboardSelectedColumn();
    T value = getVisibleItem(row);
    Object key = getValueKey(value);
    // TODO(pengzhuang): this doesn't support sub row selection?
    Context context = new Context(row + getPageStart(), col, key);
    HasCell<T, ?> column = tableBuilder.getColumn(context, value, elem);
    if (column == null) {
      // The selected element does not contain a Cell.
      return false;
    }

    resetFocusOnCellImpl(context, value, column, elem);
    return false;
  }

  @Override
  protected void setKeyboardSelected(int index, boolean selected, boolean stealFocus) {
    if (KeyboardSelectionPolicy.DISABLED == getKeyboardSelectionPolicy()
        || !isRowWithinBounds(index)) {
      return;
    }

    // If deselecting, we deselect the previous subrow.
    int subrow = lastKeyboardSelectedSubrow;
    if (selected) {
      subrow = keyboardSelectedSubrow;
      lastKeyboardSelectedSubrow = keyboardSelectedSubrow;
    }

    // Deselect the row.
    TableRowElement tr = getSubRowElement(index + getPageStart(), subrow);
    if (tr == null) {
      // The row does not exist.
      return;
    }
    String cellStyle = style.keyboardSelectedCell();
    boolean updatedSelection = !selected || isFocused || stealFocus;
    setRowStyleName(tr, style.keyboardSelectedRow(), style.keyboardSelectedRowCell(), selected);
    NodeList<TableCellElement> cells = tr.getCells();
    int keyboardColumn = Math.min(getKeyboardSelectedColumn(), cells.getLength() - 1);
    for (int i = 0; i < cells.getLength(); i++) {
      TableCellElement td = cells.getItem(i);
      boolean isKeyboardSelected = (i == keyboardColumn);

      // Update the selected style.
      setStyleName(td, cellStyle, updatedSelection && selected && isKeyboardSelected);

      // Mark as focusable.
      final Element focusable = getKeyboardSelectedElement(td);
      setFocusable(focusable, selected && isKeyboardSelected);

      // Move focus to the cell.
      if (selected && stealFocus && !cellIsEditing && isKeyboardSelected) {
        CellBasedWidgetImpl.get().resetFocus(new Scheduler.ScheduledCommand() {
          @Override
          public void execute() {
            focusable.focus();
          }
        });
      }
    }
  }

  /**
   * Get a subrow element given the index of the row value and the sub row
   * index.
   * 
   * @param absRow the absolute row value index
   * @param subrow the index of the subrow beneath the row.
   * @return the row element, or null if not found
   */
  // Visible for testing.
  TableRowElement getSubRowElement(int absRow, int subrow) {
    int relRow = absRow - getPageStart();
    checkRowBounds(relRow);

    /*
     * In most tables, the row element that represents the row object at the
     * specified index will be at the same index in the DOM. However, if the
     * user provides a TableBuilder that renders multiple rows per row value,
     * that will not be the case.
     * 
     * We use a binary search to find the row, but we start at the index as that
     * is the most likely location.
     */
    NodeList<TableRowElement> rows = getTableBodyElement().getRows();
    int rowCount = rows.getLength();
    if (rowCount == 0) {
      return null;
    }

    int frameStart = 0;
    int frameEnd = rowCount - 1;
    int domIndex = Math.min(relRow, frameEnd);
    while (domIndex >= frameStart && domIndex <= frameEnd) {
      TableRowElement curRow = rows.getItem(domIndex);
      int rowValueIndex = tableBuilder.getRowValueIndex(curRow);
      if (rowValueIndex == absRow) {
        // Found a subrow in the row index.
        int subrowValueIndex = tableBuilder.getSubrowValueIndex(curRow);
        if (subrow != subrowValueIndex) {
          // Shift to the correct subrow.
          int offset = subrow - subrowValueIndex;
          int subrowIndex = domIndex + offset;
          if (subrowIndex >= rows.getLength()) {
            // The subrow is out of range of the table.
            return null;
          }
          curRow = rows.getItem(subrowIndex);
          if (tableBuilder.getRowValueIndex(curRow) != absRow) {
            // The "subrow" is actually part of the next row.
            return null;
          }
        }
        return curRow;
      } else if (rowValueIndex > absRow) {
        // Shift the frame to lower indexes.
        frameEnd = domIndex - 1;
      } else {
        // Shift the frame to higher indexes.
        frameStart = domIndex + 1;
      }

      // Move the dom index.
      domIndex = (frameStart + frameEnd) / 2;
    }

    // The element wasn't found.
    return null;
  }

  /**
   * Build a list of row values.
   * 
   * @param values the row values to render
   * @param start the absolute start index
   * @param isRebuildingAllRows is this going to rebuild all rows
   * @return a {@link SafeHtml} string containing the row values
   */
  private SafeHtml buildRowValues(List<T> values, int start, boolean isRebuildingAllRows) {
    int length = values.size();
    int end = start + length;
    tableBuilder.start(isRebuildingAllRows);
    for (int i = start; i < end; i++) {
      T value = values.get(i - start);
      tableBuilder.buildRow(value, i);
    }

    // Update the properties of the table.
    coalesceCellProperties();
    TableSectionBuilder tableSectionBuilder = tableBuilder.finish();
    if (tableSectionBuilder instanceof HtmlTableSectionBuilder) {
      // Strip the table section tags off of the tbody.
      String rawHtml = ((HtmlTableSectionBuilder) tableSectionBuilder).asSafeHtml().asString();
      assert rawHtml.startsWith("<tbody>") : "Malformed html";
      assert rawHtml.endsWith("</tbody>") : "Malformed html";
      rawHtml = rawHtml.substring(7, rawHtml.length() - 8);
      return SafeHtmlUtils.fromTrustedString(rawHtml);
    } else {
      throw new IllegalStateException("Only HTML table section builder is supported at this time");
    }
  }

  /**
   * Check that the specified column is within bounds.
   * 
   * @param col the column index
   * @throws IndexOutOfBoundsException if the column is out of bounds
   */
  private void checkColumnBounds(int col) {
    if (col < 0 || col >= getColumnCount()) {
      throw new IndexOutOfBoundsException("Column index is out of bounds: " + col);
    }
  }

  /**
   * Coalesce the various cell properties (dependsOnSelection, handlesSelection,
   * isInteractive) into a table policy.
   */
  private void coalesceCellProperties() {
    dependsOnSelection = false;
    handlesSelection = false;
    isInteractive = false;
    for (HasCell<T, ?> column : tableBuilder.getColumns()) {
      Cell<?> cell = column.getCell();
      if (cell.dependsOnSelection()) {
        dependsOnSelection = true;
      }
      if (cell.handlesSelection()) {
        handlesSelection = true;
      }
      if (isColumnInteractive(column)) {
        isInteractive = true;
      }
    }
  }

  /**
   * Render the header or footer.
   * 
   * @param isFooter true if this is the footer table, false if the header table
   */
  private void createHeaders(boolean isFooter) {
    List<Header<?>> theHeaders = isFooter ? footers : headers;
    TableSectionElement section = isFooter ? getTableFootElement() : getTableHeadElement();
    String className = isFooter ? style.footer() : style.header();
    String firstColumnStyle =
        " " + (isFooter ? style.firstColumnFooter() : style.firstColumnHeader());
    String lastColumnStyle = " " + (isFooter ? style.lastColumnFooter() : style.lastColumnHeader());
    String sortableStyle = " " + style.sortableHeader();
    String sortedAscStyle = " " + style.sortedHeaderAscending();
    String sortedDescStyle = " " + style.sortedHeaderDescending();

    boolean hasHeader = false;
    SafeHtmlBuilder sb = new SafeHtmlBuilder();
    sb.appendHtmlConstant("<tr>");
    int columnCount = columns.size();
    if (columnCount > 0) {
      // Get information about the sorted column.
      ColumnSortInfo sortedInfo = (sortList.size() == 0) ? null : sortList.get(0);
      Column<?, ?> sortedColumn = (sortedInfo == null) ? null : sortedInfo.getColumn();
      boolean isSortAscending = (sortedInfo == null) ? false : sortedInfo.isAscending();

      // Setup the first column.
      Header<?> prevHeader = theHeaders.get(0);
      Column<T, ?> column = columns.get(0);
      int prevColspan = 1;
      boolean isSortable = false;
      boolean isSorted = false;
      StringBuilder classesBuilder = new StringBuilder(className);
      classesBuilder.append(firstColumnStyle);
      if (!isFooter && column.isSortable()) {
        isSortable = true;
        isSorted = (column == sortedColumn);
      }

      // Loop through all column headers.
      int curColumn;
      for (curColumn = 1; curColumn < columnCount; curColumn++) {
        Header<?> header = theHeaders.get(curColumn);

        if (header != prevHeader) {
          // The header has changed, so append the previous one.
          SafeHtml headerHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
          if (prevHeader != null) {
            hasHeader = true;

            // Build the header.
            SafeHtmlBuilder headerBuilder = new SafeHtmlBuilder();
            Context context = new Context(0, curColumn - prevColspan, prevHeader.getKey());
            prevHeader.render(context, headerBuilder);

            // Wrap the header with a sort icon.
            if (isSorted) {
              SafeHtml unwrappedHeader = headerBuilder.toSafeHtml();
              headerBuilder = new SafeHtmlBuilder();
              getSortDecorator(isSortAscending).render(null, unwrappedHeader, headerBuilder);
            }
            headerHtml = headerBuilder.toSafeHtml();
          }
          if (isSortable) {
            classesBuilder.append(sortableStyle);
          }
          if (isSorted) {
            classesBuilder.append(isSortAscending ? sortedAscStyle : sortedDescStyle);
          }
          sb.append(template.th(prevColspan, classesBuilder.toString(), headerHtml));

          // Reset the previous header.
          prevHeader = header;
          prevColspan = 1;
          classesBuilder = new StringBuilder(className);
          isSortable = false;
          isSorted = false;
        } else {
          // Increment the colspan if the headers == each other.
          prevColspan++;
        }

        // Update the sorted state.
        column = columns.get(curColumn);
        if (!isFooter && column.isSortable()) {
          isSortable = true;
          isSorted = (column == sortedColumn);
        }
      }

      // Append the last header.
      SafeHtml headerHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
      if (prevHeader != null) {
        hasHeader = true;

        // Build the header.
        SafeHtmlBuilder headerBuilder = new SafeHtmlBuilder();
        Context context = new Context(0, curColumn - prevColspan, prevHeader.getKey());
        prevHeader.render(context, headerBuilder);

        // Wrap the header with a sort icon.
        if (isSorted) {
          SafeHtml unwrappedHeader = headerBuilder.toSafeHtml();
          headerBuilder = new SafeHtmlBuilder();
          getSortDecorator(isSortAscending).render(null, unwrappedHeader, headerBuilder);
        }
        headerHtml = headerBuilder.toSafeHtml();
      }
      if (isSortable) {
        classesBuilder.append(sortableStyle);
      }
      if (isSorted) {
        classesBuilder.append(isSortAscending ? sortedAscStyle : sortedDescStyle);
      }

      // The first and last columns could be the same column.
      classesBuilder.append(" ");
      classesBuilder.append(lastColumnStyle);
      sb.append(template.th(prevColspan, classesBuilder.toString(), headerHtml));
    }
    sb.appendHtmlConstant("</tr>");

    // Render the section contents.
    TABLE_IMPL.replaceAllRows(this, section, sb.toSafeHtml());

    // If the section isn't used, hide it.
    doSetHeaderVisible(isFooter, hasHeader);
  }

  /**
   * Fire an event to the Cell within the specified {@link TableCellElement}.
   */
  private <C> void fireEventToCell(Event event, String eventType, Element parentElem,
      final T rowValue, Context context, HasCell<T, C> column) {
    // Check if the cell consumes the event.
    Cell<C> cell = column.getCell();
    if (!cellConsumesEventType(cell, eventType)) {
      return;
    }

    C cellValue = column.getValue(rowValue);
    boolean cellWasEditing = cell.isEditing(context, parentElem, cellValue);
    if (column instanceof Column) {
      /*
       * If the HasCell is a Column, let it handle the event itself. This is
       * here for legacy support.
       */
      Column<T, C> col = (Column<T, C>) column;
      col.onBrowserEvent(context, parentElem, rowValue, event);
    } else {
      // Create a FieldUpdater.
      final FieldUpdater<T, C> fieldUpdater = column.getFieldUpdater();
      final int index = context.getIndex();
      ValueUpdater<C> valueUpdater = (fieldUpdater == null) ? null : new ValueUpdater<C>() {
        @Override
        public void update(C value) {
          fieldUpdater.update(index, rowValue, value);
        }
      };

      // Fire the event to the cell.
      cell.onBrowserEvent(context, parentElem, cellValue, event, valueUpdater);
    }

    // Reset focus if needed.
    cellIsEditing = cell.isEditing(context, parentElem, cellValue);
    if (cellWasEditing && !cellIsEditing) {
      CellBasedWidgetImpl.get().resetFocus(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          setFocus(true);
        }
      });
    }
  }

  /**
   * Get the keyboard selected element from the selected table cell.
   * 
   * @return the keyboard selected element, or null if there is none
   */
  private Element getKeyboardSelectedElement(TableCellElement td) {
    if (td == null) {
      return null;
    }

    /*
     * The TD itself is a cell parent, which means its internal structure
     * (including the tabIndex that we set) could be modified by its Cell. We
     * return the TD to be safe.
     */
    if (tableBuilder.isColumn(td)) {
      return td;
    }

    /*
     * The default table builder adds a focusable div to the table cell because
     * TDs aren't focusable in all browsers. If the user defines a custom table
     * builder with a different structure, we must assume the keyboard selected
     * element is the TD itself.
     */
    Element firstChild = td.getFirstChildElement();
    if (firstChild != null && td.getChildCount() == 1
        && "div".equalsIgnoreCase(firstChild.getTagName())) {
      return firstChild;
    }

    return td;
  }

  /**
   * Get the {@link TableCellElement} that is currently keyboard selected.
   * 
   * @return the table cell element, or null if not selected
   */
  private TableCellElement getKeyboardSelectedTableCellElement() {
    int colIndex = getKeyboardSelectedColumn();
    if (colIndex < 0) {
      return null;
    }

    // Do not use getRowElement() because that will flush the presenter.
    int rowIndex = getKeyboardSelectedRow();
    if (rowIndex < 0 || rowIndex >= getTableBodyElement().getRows().getLength()) {
      return null;
    }
    TableRowElement tr = getSubRowElement(rowIndex + getPageStart(), keyboardSelectedSubrow);
    if (tr != null) {
      int cellCount = tr.getCells().getLength();
      if (cellCount > 0) {
        int column = Math.min(colIndex, cellCount - 1);
        return tr.getCells().getItem(column);
      }
    }
    return null;
  }
  
  /**
   * Get the {@link IconCellDecorator} used to decorate sorted column headers.
   * 
   * @param ascending true if ascending, false if descending
   * @return the {@link IconCellDecorator}
   */
  private IconCellDecorator<SafeHtml> getSortDecorator(boolean ascending) {
    if (ascending) {
      if (sortAscDecorator == null) {
        sortAscDecorator =
            new IconCellDecorator<SafeHtml>(resources.sortAscending(), new SafeHtmlCell());
      }
      return sortAscDecorator;
    } else {
      if (sortDescDecorator == null) {
        sortDescDecorator =
            new IconCellDecorator<SafeHtml>(resources.sortDescending(), new SafeHtmlCell());
      }
      return sortDescDecorator;
    }
  }

  /**
   * Initialize the widget.
   */
  private void init() {
    if (TABLE_IMPL == null) {
      TABLE_IMPL = GWT.create(Impl.class);
    }
    if (template == null) {
      template = GWT.create(Template.class);
    }

    // Sink events.
    Set<String> eventTypes = new HashSet<String>();
    eventTypes.add("mouseover");
    eventTypes.add("mouseout");
    CellBasedWidgetImpl.get().sinkEvents(this, eventTypes);

    // Set the table builder.
    tableBuilder = new DefaultCellTableBuilder<T>(this);

    // Set the keyboard handler.
    setKeyboardSelectionHandler(new CellTableKeyboardSelectionHandler<T>(this));
  }

  /**
   * Mark the column widths as dirty and redraw the table.
   */
  private void refreshColumnsAndRedraw() {
    columnWidthsDirty = true;
    redraw();
  }

  /**
   * Refresh the headers and column widths.
   */
  private void refreshHeadersAndColumnsImpl() {
    // Refresh the column widths if needed.
    if (columnWidthsDirty) {
      columnWidthsDirty = false;
      refreshColumnWidths();
    }

    // Render the headers and footers.
    boolean wereHeadersDirty = headersDirty;
    headersDirty = false;
    if (wereHeadersDirty || !headerRefreshDisabled) {
      createHeaders(false);
    }
    if (wereHeadersDirty || !footerRefreshDisabled) {
      createHeaders(true);
    }
  }

  private <C> boolean resetFocusOnCellImpl(Context context, T value, HasCell<T, C> column,
      Element cellParent) {
    C cellValue = column.getValue(value);
    Cell<C> cell = column.getCell();
    return cell.resetFocus(context, cellParent, cellValue);
  }

  /**
   * Apply a style to a row and all cells in the row.
   * 
   * @param tr the row element
   * @param rowStyle the style to apply to the row
   * @param cellStyle the style to apply to the cells
   * @param add true to add the style, false to remove
   */
  private void setRowStyleName(TableRowElement tr, String rowStyle, String cellStyle, boolean add) {
    setStyleName(tr, rowStyle, add);
    NodeList<TableCellElement> cells = tr.getCells();
    for (int i = 0; i < cells.getLength(); i++) {
      setStyleName(cells.getItem(i), cellStyle, add);
    }
  }

  /**
   * Update the width of all instances of the specified column. A column
   * instance may appear multiple times in the table.
   * 
   * @param column the column to update
   * @param width the width of the column, or null to clear the width
   */
  private void updateColumnWidthImpl(Column<T, ?> column, String width) {
    // TODO(jlabanca): Use realColumnCount when headerBuilder lands.
    int columnCount = getColumnCount();
    for (int i = 0; i < columnCount; i++) {
      if (columns.get(i) == column) {
        doSetColumnWidth(i, width);
      }
    }
  }
}

