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
package com.google.gwt.sample.expenses.client;

import com.google.gwt.bikeshed.cells.client.TextCell;
import com.google.gwt.bikeshed.list.client.Column;
import com.google.gwt.bikeshed.list.client.Header;
import com.google.gwt.bikeshed.list.client.PagingTableListView;
import com.google.gwt.bikeshed.list.shared.ListListModel;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.HeadingElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.List;

/**
 * Interim table based implementation of {@link EntityListView}. Will be replaced
 * by some descendant of {@link com.google.gwt.bikeshed.list.client.PagingTableListView<}
 */
public class TableEntityListView extends Composite implements EntityListView {
  interface Binder extends UiBinder<HTMLPanel, TableEntityListView> {
  }

  private static final Binder BINDER = GWT.create(Binder.class);

   @UiField SimplePanel body;
   @UiField HeadingElement heading;
   
   private final TextCell textCell = new TextCell();

  public TableEntityListView() {
    initWidget(BINDER.createAndBindUi(this));
  }

  public void setHeading(String text) {
    heading.setInnerText(text);
  }

  public void setRowData(final List<String> columnNames, List<Row> newValues) {
    ListListModel<Row> model = new ListListModel<Row>();
    List<Row> list = model.getList();
    list.addAll(newValues);
    
    PagingTableListView<Row> table = new PagingTableListView<Row>(model, 100);
    
    Column<Row, Command, Void> showColumn =
      new Column<Row, Command, Void>(new CommandCell("Show")) {
      @Override
      public Command getValue(Row object) {
        return object.getShowDetailsCommand();
      }
    };
    Header<String> showHeader = new Header<String>(textCell);
    showHeader.setValue("Show");
    table.addColumn(showColumn, showHeader);
    
    Column<Row, Command, Void> editColumn =
      new Column<Row, Command, Void>(new CommandCell("Edit")) {
      @Override
      public Command getValue(Row object) {
        return object.getShowDetailsCommand();
      }
    };
    Header<String> editHeader = new Header<String>(textCell);
    editHeader.setValue("Show");
    table.addColumn(editColumn, editHeader);

    for (int i = 0; i < columnNames.size(); i++) {
      final int index = i;
      Column<Row, String, Void> column =
        new Column<Row, String, Void>(textCell) {
        @Override
        public String getValue(Row object) {
          return object.getValues().get(index);
        }
      };
      Header<String> header = new Header<String>(textCell);
      header.setValue(columnNames.get(i));
      table.addColumn(column, header);
    }
    
    body.setWidget(table);
  }
}
