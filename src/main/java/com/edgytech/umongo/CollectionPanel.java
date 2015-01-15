/**
 * Copyright (C) 2010 EdgyTech LLC.
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
package com.edgytech.umongo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;

import javax.swing.JPanel;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.ComboBox;
import com.edgytech.swingfast.ConfirmDialog;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.FormDialog;
import com.edgytech.swingfast.InfoDialog;
import com.edgytech.swingfast.ListArea;
import com.edgytech.swingfast.MenuItem;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.CollectionPanel.Item;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.GroupCommand;
import com.mongodb.LazyDBDecoder;
import com.mongodb.MapReduceCommand;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.MapReduceOutput;
import com.mongodb.MongoClient;
import com.mongodb.MongoException.DuplicateKey;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

/**
 *
 * @author antoine
 */
public class CollectionPanel extends BasePanel implements EnumListener<Item> {

  enum Item {

    icon, name, fullName, queryOptions, writeConcern, readPreference, stats, getStats, getIndexes, refresh, dropCollection, rename, newName, dropTarget, doImport, export, find, findQuery, findFields, findSort, findSkip, findLimit, findBatchSize, findExplain, findExport, findHint, foQuery, foFields, count, countQuery, countSkip, countLimit, remove, removeQuery, rmAllConfirm, mapReduce, mrMap, mrReduce, mrFinalize, mrQuery, mrSort, mrType, mrOut, mrOutDB, mrOutSharded, mrNonAtomic, mrLimit, mrJSMode, group, grpKeys, grpQuery, grpInitialValue, grpReduce, grpFinalize, readWriteOptions, insert, insertDoc, insertCount, insertBulk, createIndex, findAndModify, famQuery, famFields, famSort, famUpdate, famRemove, famReturnNew, famUpsert, update, upQuery, upUpdate, upUpsert, upMulti, upSafe, save, saveDoc, shardingInfo, shardingDistribution, shardCollection, shardKeyCombo, shardCustomKey, shardUniqueIndex, listChunks, validate, validateFull, touch, touchData, touchIndex, compact, compactForce, reIndex, moveChunk, mvckQuery, mvckToShard, splitChunk, spckQuery, spckOnValue, geoNear, gnOrigin, gnLimit, gnMaxDistance, gnDistanceMultiplier, gnQuery, gnSpherical, gnSearch, lazyDecoding, fixCollection, fcDialog, fcSrcMongo, fcUpsert, fullTextSearch, ftsSearch, ftsFilter, ftsProject, ftsLimit, ftsLanguage, aggregate, settings, usePowerOf2Sizes, manageTagRanges, tagRangeList, tagRangeDialog, addTagRange, editTagRange, removeTagRange, distinct, distinctKey, distinctQuery, summarizeData
  }

  public CollectionPanel() {
    setEnumBinding(Item.values(), this);
  }

  public CollectionNode getCollectionNode() {
    return (CollectionNode) getNode();
  }

  public BasicDBObject getStats() {
    return getCollectionNode().getStats();
  }

  @Override
  protected void updateComponentCustom(final JPanel old) {
    try {
      final DBCollection collection = getCollectionNode().getCollection();
      setStringFieldValue(Item.name, collection.getName());
      setStringFieldValue(Item.fullName, collection.getFullName());
      setStringFieldValue(Item.queryOptions, MongoUtils.queryOptionsToString(collection.getOptions()));
      ((DocField) getBoundUnit(Item.writeConcern)).setDoc(collection.getWriteConcern().getCommand());
      ((DocField) getBoundUnit(Item.readPreference)).setDoc(collection.getReadPreference().toDBObject());
      ((CmdField) getBoundUnit(Item.stats)).updateFromCmd(collection);
    } catch (final Exception e) {
      UMongo.instance.showError(this.getClass().getSimpleName() + " update", e);
    }
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
    if (enm == Item.lazyDecoding) {
      final boolean lazy = getBooleanFieldValue(Item.lazyDecoding);
      final DBCollection col = getCollectionNode().getCollection();
      if (lazy) {
        col.setDBDecoderFactory(LazyDBDecoder.FACTORY);
      } else {
        col.setDBDecoderFactory(null);
      }
    }
  }

  public void find(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.findQuery)).getDBObject();
    final DBObject fields = ((DocBuilderField) getBoundUnit(Item.findFields)).getDBObject();
    final DBObject sort = ((DocBuilderField) getBoundUnit(Item.findSort)).getDBObject();
    final DBObject hint = ((DocBuilderField) getBoundUnit(Item.findHint)).getDBObject();
    final int skip = getIntFieldValue(Item.findSkip);
    final int limit = getIntFieldValue(Item.findLimit);
    final int batchSize = getIntFieldValue(Item.findBatchSize);
    final boolean explain = getBooleanFieldValue(Item.findExplain);
    final boolean export = getBooleanFieldValue(Item.findExport);

    if (export) {
      exportToFile(col, query, fields, sort, skip, limit, batchSize);
    } else {
      new DbJob() {
        @Override
        public Object doRun() {
          // this does not actually block, may not need dbjob
          final DBCursor cur = col.find(query, fields, skip, batchSize);
          if (sort != null) {
            cur.sort(sort);
          }
          if (limit > 0) {
            cur.limit(limit);
          }
          if (hint != null) {
            cur.hint(hint);
          }
          if (explain) {
            return cur.explain();
          }

          // force cursor to start
          cur.hasNext();
          return cur;
        }

        @Override
        public String getNS() {
          return col.getFullName();
        }

        @Override
        public String getShortName() {
          return "Find";
        }

        @Override
        public DBObject getRoot(final Object result) {
          if (result == null || !(result instanceof DBCursor)) {
            return null;
          }
          final DBCursor res = (DBCursor) result;
          final BasicDBObject obj = new BasicDBObject("cursorId", res.getCursorId());
          obj.put("server", res.getServerAddress().toString());
          obj.put("query", res.getQuery());
          obj.put("fields", res.getKeysWanted());
          obj.put("options", res.getOptions());
          obj.put("readPreference", res.getReadPreference().toDBObject());
          obj.put("numSeen", res.numSeen());
          obj.put("numGetMores", res.numGetMores());
          // want skip, limit, batchsize
          return obj;
        }

        @Override
        public ButtonBase<?, ?> getButton() {
          return button;
        }
      }.addJob();

    }
  }

  // public void findOne(final ButtonBase<?, ?> button) {
  // final DBCollection col = getCollectionNode().getCollection();
  // final DBObject query = ((DocBuilderField)
  // getBoundUnit(Item.foQuery)).getDBObject();
  // final DBObject fields = ((DocBuilderField)
  // getBoundUnit(Item.foFields)).getDBObject();
  //
  // new DbJob() {
  //
  // @Override
  // public Object doRun() {
  // return col.findOne(query, fields);
  // }
  //
  // @Override
  // public String getNS() {
  // return col.getFullName();
  // }
  //
  // @Override
  // public String getShortName() {
  // return "FindOne";
  // }
  //
  // @Override
  // public DBObject getRoot(Object result) {
  // DBObject root = new BasicDBObject("query", query);
  // root.put("fields", fields);
  // return root;
  // }
  //
  // @Override
  // public ButtonBase<?, ?> getButton() {
  // return button;
  // }
  // }.addJob();
  // }
  public void rename(final ButtonBase<?, ?> button) {
    final CollectionNode colNode = getCollectionNode();
    final DBCollection col = colNode.getCollection();
    // select parent since this is getting renamed
    UMongo.instance.displayNode(colNode.getDbNode());

    final String name = getStringFieldValue(Item.newName);
    final boolean dropTarget = getBooleanFieldValue(Item.dropTarget);

    final DBObject cmd = BasicDBObjectBuilder.start().add("renameCollection", col.getFullName()).add("to", col.getDB().getName() + "." + name).add("dropTarget", dropTarget).get();
    new DbJobCmd(col.getDB().getSisterDB("admin"), cmd, null, null).addJob();
  }

  public void group(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final DBObject keys = ((DocBuilderField) getBoundUnit(Item.grpKeys)).getDBObject();
    final DBObject initial = ((DocBuilderField) getBoundUnit(Item.grpInitialValue)).getDBObject();
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.grpQuery)).getDBObject();
    final String reduce = getStringFieldValue(Item.grpReduce);
    final String finalize = getStringFieldValue(Item.grpFinalize);
    final GroupCommand cmd = new GroupCommand(col, keys, query, initial, reduce, finalize);
    // new DocView(null, "Group", col.getDB(),
    // cmd.toDBObject()).addToTabbedDiv();

    new DbJobCmd(col.getDB(), cmd.toDBObject(), null, button).addJob();
  }

  public void distinct(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final BasicDBObject cmd = new BasicDBObject("distinct", col.getName());
    cmd.put("key", getStringFieldValue(Item.distinctKey));
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.distinctQuery)).getDBObject();
    if (query != null) {
      cmd.put("query", query);
    }
    new DbJobCmd(col.getDB(), cmd, null, button).addJob();
  }

  public void mapReduce(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final String map = getStringFieldValue(Item.mrMap);
    final String reduce = getStringFieldValue(Item.mrReduce);
    final String finalize = getStringFieldValue(Item.mrFinalize);
    final String stype = getStringFieldValue(Item.mrType);
    final OutputType type = OutputType.valueOf(stype.toUpperCase());
    final String out = getStringFieldValue(Item.mrOut);
    if (type != OutputType.INLINE && out.isEmpty()) {
      new InfoDialog(id, null, null, "Output collection cannot be empty if type is not inline.").show();
      return;
    }

    final String outDB = getStringFieldValue(Item.mrOutDB);
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.mrQuery)).getDBObject();
    final int limit = getIntFieldValue(Item.mrLimit);
    final MapReduceCommand cmd = new MapReduceCommand(col, map, reduce, out, type, query);
    final DBObject sort = ((DocBuilderField) getBoundUnit(Item.mrSort)).getDBObject();
    if (sort != null) {
      cmd.setSort(sort);
    }
    if (!outDB.isEmpty()) {
      cmd.setOutputDB(outDB);
    }
    if (!finalize.isEmpty()) {
      cmd.setFinalize(finalize);
    }
    if (limit > 0) {
      cmd.setLimit(limit);
    }

    if (getBooleanFieldValue(Item.mrJSMode)) {
      cmd.addExtraOption("jsMode", true);
    }

    final BasicDBObject cmdobj = (BasicDBObject) cmd.toDBObject();
    if (getBooleanFieldValue(Item.mrOutSharded)) {
      ((BasicDBObject) cmdobj.get("out")).put("sharded", true);
    }
    if (getBooleanFieldValue(Item.mrNonAtomic)) {
      ((BasicDBObject) cmdobj.get("out")).put("nonAtomic", true);
    }

    new DbJob() {
      MapReduceOutput output;

      @Override
      public Object doRun() {
        // output = col.mapReduce(cmd);

        // if type in inline, then query options like slaveOk is fine
        CommandResult res = null;
        if (type == MapReduceCommand.OutputType.INLINE) {
          res = col.getDB().command(cmdobj, col.getOptions());
          return res;
        }

        res = col.getDB().command(cmdobj);
        res.throwOnError();
        output = new MapReduceOutput(col, cmdobj, res);
        return output;
      }

      @Override
      public void wrapUp(Object res) {
        if (output != null) {
          if (cmd.getOutputType() == OutputType.INLINE) {
            res = output.results();
          } else {
            // spawn a find
            doFind(output.getOutputCollection(), null);
            res = output.getRaw();
          }
        }
        super.wrapUp(res);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "MR";
      }

      @Override
      public DBObject getRoot(final Object result) {
        return cmdobj;
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }

      @Override
      DBObject getCommand() {
        return cmdobj;
      }

      @Override
      DB getDB() {
        return col.getDB();
      }
    }.addJob();
  }

  static void doFind(final DBCollection col, final DBObject query) {
    doFind(col, query, null, null, 0, 0, 0, false, null, 0);
  }

  static void doFind(final DBCollection col, final DBObject query, final DBObject fields, final DBObject sort, final int skip, final int limit, final int batchSize, final boolean explain, final DBObject hint, final int options) {
    new DbJob() {
      @Override
      public Object doRun() {
        // this does not actually block, may not need dbjob
        final DBCursor cur = col.find(query, fields).skip(skip).batchSize(batchSize).addOption(options);
        if (sort != null) {
          cur.sort(sort);
        }
        if (limit > 0) {
          cur.limit(limit);
        }
        if (hint != null) {
          cur.hint(hint);
        }
        if (explain) {
          return cur.explain();
        }

        // force cursor to start
        cur.hasNext();
        return cur;
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Find";
      }

      @Override
      public DBObject getRoot(final Object result) {
        if (result == null || !(result instanceof DBCursor)) {
          return null;
        }
        final DBCursor res = (DBCursor) result;
        final BasicDBObject obj = new BasicDBObject("cursorId", res.getCursorId());
        obj.put("query", res.getQuery());
        obj.put("fields", res.getKeysWanted());
        obj.put("options", res.getOptions());
        obj.put("readPreference", res.getReadPreference().toDBObject());
        obj.put("numSeen", res.numSeen());
        obj.put("numGetMores", res.numGetMores());
        // want skip, limit, batchsize
        return obj;
      }
    }.addJob();
  }

  private void exportToFile(final DBCollection col, final DBObject query, final DBObject fields, final DBObject sort, final int skip, final int limit, final int batchSize) {
    final ExportDialog dia = UMongo.instance.getGlobalStore().getExportDialog();
    if (!dia.show()) {
      return;
    }
    final DocumentSerializer ds = dia.getDocumentSerializer();
    final boolean continueOnError = dia.getBooleanFieldValue(ExportDialog.Item.continueOnError);
    new DbJob() {
      @Override
      public Object doRun() throws Exception {
        try {
          try {
            final DBCursor cur = col.find(query, fields);
            if (skip > 0) {
              cur.skip(skip);
            }
            if (batchSize != 0) {
              cur.batchSize(batchSize);
            }
            if (sort != null) {
              cur.sort(sort);
            }
            if (limit > 0) {
              cur.limit(limit);
            }
            while (cur.hasNext() && !stopped) {
              ds.writeObject(cur.next());
            }
          } catch (final Exception e) {
            if (continueOnError) {
              getLogger().log(Level.WARNING, null, e);
            } else {
              throw e;
            }
          }
        } finally {
          ds.close();
        }
        return null;
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Export";
      }
    }.addJob();
  }

  public void dropCollection(final ButtonBase<?, ?> button) {
    final CollectionNode colNode = getCollectionNode();
    final DBCollection col = getCollectionNode().getCollection();
    final BasicDBObject cmd = new BasicDBObject("drop", col.getName());
    new DbJobCmd(col.getDB(), cmd, null, colNode.getDbNode(), null).addJob();
  }

  public void readWriteOptions(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final OptionDialog od = UMongo.instance.getGlobalStore().getOptionDialog();
    od.update(col.getOptions(), col.getWriteConcern(), col.getReadPreference());
    if (!od.show()) {
      return;
    }
    col.setOptions(od.getQueryOptions());
    col.setWriteConcern(od.getWriteConcern());
    col.setReadPreference(od.getReadPreference());
    refresh();
  }

  public void insert(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final BasicDBObject doc = (BasicDBObject) ((DocBuilderField) getBoundUnit(Item.insertDoc)).getDBObject();
    final int count = getIntFieldValue(Item.insertCount);
    final boolean bulk = getBooleanFieldValue(Item.insertBulk);

    new DbJob() {
      @Override
      public Object doRun() throws IOException {
        WriteResult res = null;
        final List<DBObject> list = new ArrayList<DBObject>();
        for (int i = 0; i < count; ++i) {
          final BasicDBObject newdoc = (BasicDBObject) doc.copy();
          handleSpecialFields(newdoc);
          if (bulk) {
            list.add(newdoc);
            if (list.size() >= 1000) {
              res = col.insert(list);
              list.clear();
            }
          } else {
            res = col.insert(newdoc);
          }
        }
        if (bulk && !list.isEmpty()) {
          return col.insert(list);
        }
        return res;
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Insert";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject root = new BasicDBObject("doc", doc);
        root.put("count", count);
        root.put("bulk", bulk);
        return root;
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }
    }.addJob();
  }

  public void save(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final BasicDBObject doc = (BasicDBObject) ((DocBuilderField) getBoundUnit(Item.saveDoc)).getDBObject();

    new DbJob() {
      @Override
      public Object doRun() throws IOException {
        return col.save((DBObject) doc.copy());
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Save";
      }

      @Override
      public DBObject getRoot(final Object result) {
        return doc;
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }
    }.addJob();
  }

  public void remove(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final BasicDBObject tmp = (BasicDBObject) ((DocBuilderField) getBoundUnit(Item.removeQuery)).getDBObject();
    final BasicDBObject doc = tmp != null ? tmp : new BasicDBObject();

    if (doc.isEmpty()) {
      final ConfirmDialog confirm = (ConfirmDialog) getBoundUnit(Item.rmAllConfirm);
      if (!confirm.show()) {
        return;
      }
    }

    new DbJob() {
      @Override
      public Object doRun() throws IOException {
        return col.remove(doc);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Remove";
      }

      @Override
      public DBObject getRoot(final Object result) {
        return new BasicDBObject("query", doc);
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }
    }.addJob();
  }

  public void createIndex(final ButtonBase<?, ?> button) {
    final CollectionNode node = getCollectionNode();
    final DBCollection col = getCollectionNode().getCollection();
    final CreateIndexDialog dia = (CreateIndexDialog) button.getDialog();
    final DBObject keys = dia.getKeys();
    final DBObject opts = dia.getOptions();

    if (!UMongo.instance.getGlobalStore().confirmLockingOperation()) {
      return;
    }

    new DbJob() {
      @Override
      public Object doRun() throws IOException {
        col.createIndex(keys, opts);
        return new BasicDBObject("ok", 1);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Ensure Index";
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        node.structureComponent();
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject obj = new BasicDBObject("keys", keys);
        obj.put("options", opts);
        return obj;
      }
    }.addJob();
  }

  public void count(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.countQuery)).getDBObject();
    final int skip = getIntFieldValue(Item.countSkip);
    final int limit = getIntFieldValue(Item.countLimit);

    final BasicDBObject cmd = new BasicDBObject();
    cmd.put("count", col.getName());
    cmd.put("query", query);

    if (limit > 0) {
      cmd.put("limit", limit);
    }
    if (skip > 0) {
      cmd.put("skip", skip);
    }
    new DbJobCmd(col.getDB(), cmd, null, button).addJob();
  }

  public void findAndModify(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.famQuery)).getDBObject();
    final DBObject fields = ((DocBuilderField) getBoundUnit(Item.famFields)).getDBObject();
    final DBObject sort = ((DocBuilderField) getBoundUnit(Item.famSort)).getDBObject();
    final BasicDBObject update = (BasicDBObject) ((DocBuilderField) getBoundUnit(Item.famUpdate)).getDBObject();
    final boolean remove = getBooleanFieldValue(Item.famRemove);
    final boolean returnNew = getBooleanFieldValue(Item.famReturnNew);
    final boolean upsert = getBooleanFieldValue(Item.famUpsert);

    final BasicDBObject cmd = new BasicDBObject("findandmodify", col.getName());
    if (query != null && !query.keySet().isEmpty()) {
      cmd.append("query", query);
    }
    if (fields != null && !fields.keySet().isEmpty()) {
      cmd.append("fields", fields);
    }
    if (sort != null && !sort.keySet().isEmpty()) {
      cmd.append("sort", sort);
    }

    if (remove) {
      cmd.append("remove", remove);
    } else {
      if (update != null && !update.keySet().isEmpty()) {
        // if 1st key doesn't start with $, then object will be inserted as is,
        // need to check it
        final String key = update.keySet().iterator().next();
        if (key.charAt(0) != '$') {
          MongoUtils.checkObject(update, false, false);
        }
        cmd.append("update", update.copy());
      }
      if (returnNew) {
        cmd.append("new", returnNew);
      }
      if (upsert) {
        cmd.append("upsert", upsert);
      }
    }

    new DbJobCmd(col.getDB(), cmd, null, button).addJob();
  }

  public void update(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection();
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.upQuery)).getDBObject();
    final BasicDBObject update = (BasicDBObject) ((DocBuilderField) getBoundUnit(Item.upUpdate)).getDBObject();
    final boolean upsert = getBooleanFieldValue(Item.upUpsert);
    final boolean multi = getBooleanFieldValue(Item.upMulti);
    final boolean safe = getBooleanFieldValue(Item.upSafe);
    col.setWriteConcern(WriteConcern.SAFE);

    new DbJob() {
      @Override
      public Object doRun() {
        if (safe) {
          final long count = col.getCount(query);
          long toupdate = count > 0 ? 1 : 0;
          if (multi) {
            toupdate = count;
          }
          final String text = "Proceed with updating " + toupdate + " of " + count + " documents?";
          final ConfirmDialog confirm = new ConfirmDialog(null, "Confirm Update", null, text);
          if (!confirm.show()) {
            return null;
          }
        }
        return col.update(query, (DBObject) update.copy(), upsert, multi);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Update";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject obj = new BasicDBObject("query", query);
        obj.put("update", update);
        obj.put("upsert", upsert);
        obj.put("multi", multi);
        return obj;
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }
    }.addJob();
  }

  public void doImport(final ButtonBase<?, ?> button) throws IOException {
    final ImportDialog dia = UMongo.instance.getGlobalStore().getImportDialog();
    if (!dia.show()) {
      return;
    }
    final boolean dropCollection = dia.getBooleanFieldValue(ImportDialog.Item.dropCollection);
    final boolean continueOnError = dia.getBooleanFieldValue(ImportDialog.Item.continueOnError);
    final boolean upsert = dia.getBooleanFieldValue(ImportDialog.Item.upsert);
    final boolean bulk = dia.getBooleanFieldValue(ImportDialog.Item.bulk);
    final String supsertFields = dia.getStringFieldValue(ImportDialog.Item.upsertFields);
    final String[] upsertFields = supsertFields != null ? supsertFields.split(",") : null;
    if (upsertFields != null) {
      for (int i = 0; i < upsertFields.length; ++i) {
        upsertFields[i] = upsertFields[i].trim();
      }
    }
    final DocumentDeserializer dd = dia.getDocumentDeserializer();
    final DBCollection col = getCollectionNode().getCollection();

    new DbJob() {
      @Override
      public Object doRun() throws Exception {
        try {
          if (dropCollection) {
            col.drop();
          }
          DBObject obj = null;
          final List<DBObject> batch = new ArrayList<DBObject>();
          while ((obj = dd.readObject()) != null) {
            try {
              if (upsert) {
                if (upsertFields == null) {
                  col.save(obj);
                } else {
                  final BasicDBObject query = new BasicDBObject();
                  for (final String upsertField : upsertFields) {
                    final String field = upsertField;
                    if (!obj.containsField(field)) {
                      throw new Exception("Upsert field " + field + " not present in object " + obj.toString());
                    }
                    query.put(field, obj.get(field));
                  }
                  col.update(query, obj, true, false);
                }
              } else {
                if (bulk) {
                  if (batch.size() > 1000) {
                    col.insert(batch);
                    batch.clear();
                  }
                  batch.add(obj);
                } else {
                  col.insert(obj);
                }
              }
            } catch (final Exception e) {
              if (continueOnError) {
                getLogger().log(Level.WARNING, null, e);
              } else {
                throw e;
              }
            }
          }

          if (!batch.isEmpty()) {
            col.insert(batch);
          }

        } finally {
          dd.close();
        }
        return null;
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Import";
      }

      @Override
      public ButtonBase<?, ?> getButton() {
        return button;
      }
    }.addJob();
  }

  public void export(final ButtonBase<?, ?> button) {
    exportToFile(getCollectionNode().getCollection(), null, null, null, 0, 0, 0);
  }

  public void getStats(final ButtonBase<?, ?> button) {
    new DbJobCmd(getCollectionNode().getCollection(), "collstats").addJob();
  }

  public void getIndexes(final ButtonBase<?, ?> button) {
    final DBCollection col = getCollectionNode().getCollection().getDB().getCollection("system.indexes");
    CollectionPanel.doFind(col, new BasicDBObject("ns", getCollectionNode().getCollection().getFullName()));
  }

  public void validate(final ButtonBase<?, ?> button) {
    final BasicDBObject cmd = new BasicDBObject("validate", getCollectionNode().getCollection().getName());
    if (getBooleanFieldValue(Item.validateFull)) {
      cmd.put("full", true);
    }
    new DbJobCmd(getCollectionNode().getDbNode().getDb(), cmd, null, button).addJob();
  }

  public void touch(final ButtonBase<?, ?> button) {
    final BasicDBObject cmd = new BasicDBObject("touch", getCollectionNode().getCollection().getName());
    cmd.put("data", getBooleanFieldValue(Item.touchData));
    cmd.put("index", getBooleanFieldValue(Item.touchIndex));
    new DbJobCmd(getCollectionNode().getDbNode().getDb(), cmd, null, button).addJob();
  }

  public void compact(final ButtonBase<?, ?> button) {
    final BasicDBObject cmd = new BasicDBObject("compact", getCollectionNode().getCollection().getName());
    if (getBooleanFieldValue(Item.compactForce)) {
      cmd.put("force", true);
    }
    if (!UMongo.instance.getGlobalStore().confirmLockingOperation()) {
      return;
    }
    new DbJobCmd(getCollectionNode().getDbNode().getDb(), cmd, null, button).addJob();
  }

  public void reIndex(final ButtonBase<?, ?> button) {
    final BasicDBObject cmd = new BasicDBObject("reIndex", getCollectionNode().getCollection().getName());
    if (!UMongo.instance.getGlobalStore().confirmLockingOperation()) {
      return;
    }
    new DbJobCmd(getCollectionNode().getDbNode().getDb(), cmd, null, button).addJob();
  }

  public void shardingInfo(final ButtonBase<?, ?> button) {
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");
    final DBCollection col = config.getCollection("collections");
    CollectionPanel.doFind(col, new BasicDBObject("_id", getCollectionNode().getCollection().getFullName()));
  }

  public void shardingDistribution(final ButtonBase<?, ?> button) {
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");

    new DbJob() {
      @Override
      public Object doRun() throws Exception {
        final BasicDBObject result = new BasicDBObject();
        final BasicDBList shardList = new BasicDBList();
        final BasicDBObject stats = getStats();
        final BasicDBObject shards = (BasicDBObject) stats.get("shards");
        if (shards == null || shards.isEmpty()) {
          return null;
        }

        long totalChunks = 0;
        final long totalSize = stats.getLong("size");
        final long totalCount = stats.getLong("count");

        for (final Entry<?, ?> shard : shards.entrySet()) {
          final String shardName = (String) shard.getKey();
          final BasicDBObject shardStats = (BasicDBObject) shard.getValue();

          final BasicDBObject query = new BasicDBObject("ns", getCollectionNode().getCollection().getFullName());
          query.put("shard", shardName);
          final long numChunks = config.getCollection("chunks").count(query);
          totalChunks += numChunks;

          final double estChunkData = numChunks <= 0 ? 0 : shardStats.getLong("size") / numChunks;
          final long estChunkCount = numChunks <= 0 ? 0 : (long) Math.floor(shardStats.getLong("count") / numChunks);

          final BasicDBObject shardDetails = new BasicDBObject("shard", shardName);
          shardDetails.put("data", shardStats.getLong("size"));
          shardDetails.put("pctData", totalSize <= 0 ? 0 : shardStats.getLong("size") * 100.0 / totalSize);
          shardDetails.put("docs", shardStats.getLong("count"));
          shardDetails.put("pctDocs", totalCount <= 0 ? 0 : shardStats.getLong("count") * 100.0 / totalCount);
          shardDetails.put("chunks", numChunks);
          if (shardStats.containsField("avgObjSize")) {
            shardDetails.put("avgDocSize", shardStats.getDouble("avgObjSize"));
          }
          shardDetails.put("estimatedDataPerChunk", estChunkData);
          shardDetails.put("estimatedDocsPerChunk", estChunkCount);
          shardList.add(shardDetails);
        }
        result.put("shards", shardList);

        final BasicDBObject total = new BasicDBObject();
        total.put("data", totalSize);
        total.put("docs", totalCount);
        total.put("chunks", totalChunks);
        total.put("avgDocSize", stats.getDouble("avgObjSize"));
        result.put("total", total);
        return result;
      }

      @Override
      public String getNS() {
        return getCollectionNode().getCollection().getFullName();
      }

      @Override
      public String getShortName() {
        return "Sharding Distribution";
      }
    }.addJob();
  }

  public void listChunks(final ButtonBase<?, ?> button) {
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");
    final DBCollection col = config.getCollection("chunks");
    CollectionPanel.doFind(col, new BasicDBObject("ns", getCollectionNode().getCollection().getFullName()));
  }

  public void moveChunk(final ButtonBase<?, ?> button) {
    final FormDialog dialog = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.moveChunk)).getDialog();
    final ComboBox combo = (ComboBox) getBoundUnit(Item.mvckToShard);
    combo.value = 0;
    combo.items = getCollectionNode().getDbNode().getMongoNode().getShardNames();
    combo.structureComponent();

    if (!dialog.show()) {
      return;
    }

    final BasicDBObject cmd = new BasicDBObject("moveChunk", getCollectionNode().getCollection().getFullName());
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.mvckQuery)).getDBObject();
    cmd.append("find", query);
    cmd.append("to", getStringFieldValue(Item.mvckToShard));
    new DbJobCmd(getCollectionNode().getDbNode().getDb().getSisterDB("admin"), cmd).addJob();

  }

  public void splitChunk(final ButtonBase<?, ?> button) {
    final BasicDBObject cmd = new BasicDBObject("split", getCollectionNode().getCollection().getFullName());
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.spckQuery)).getDBObject();
    if (getBooleanFieldValue(Item.spckOnValue)) {
      cmd.append("middle", query);
    } else {
      cmd.append("find", query);
    }
    new DbJobCmd(getCollectionNode().getDbNode().getDb().getSisterDB("admin"), cmd).addJob();
  }

  public void shardCollection(final ButtonBase<?, ?> button) {
    final FormDialog dialog = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.shardCollection)).getDialog();
    final ComboBox combo = (ComboBox) getBoundUnit(Item.shardKeyCombo);
    combo.value = 0;
    final List<DBObject> indices = getCollectionNode().getCollection().getIndexInfo();
    final String[] items = new String[indices.size() + 1];
    items[0] = "None";
    int i = 1;
    for (final DBObject index : indices) {
      items[i++] = ((DBObject) index.get("key")).toString();
    }
    combo.items = items;
    combo.structureComponent();

    if (!dialog.show()) {
      return;
    }

    DBObject key = null;
    final int index = combo.getComponentIntValue();
    if (index > 0) {
      key = (DBObject) indices.get(index - 1).get("key");
    } else {
      key = ((DocBuilderField) getBoundUnit(Item.shardCustomKey)).getDBObject();
    }

    if (key == null) {
      new InfoDialog(null, "Empty key", null, "You must select a shard key").show();
      return;
    }
    if (!new ConfirmDialog(null, "Confirm shard key", null, "About to shard collection with key " + key + ", is it correct? This operation cannot be undone.").show()) {
      return;
    }

    final boolean unique = getBooleanFieldValue(Item.shardUniqueIndex);
    final DB admin = getCollectionNode().getDbNode().getDb().getSisterDB("admin");
    final DBObject cmd = new BasicDBObject("shardCollection", getCollectionNode().getCollection().getFullName());
    cmd.put("key", key);
    if (unique) {
      cmd.put("unique", unique);
    }
    new DbJobCmd(admin, cmd, this, null).addJob();

  }

  private Object handleSpecialFields(final DBObject doc) {
    for (final String field : doc.keySet()) {
      if (field.equals("__rand")) {
        final String type = (String) doc.get(field);
        if (type.equals("int")) {
          final int min = (Integer) doc.get("min");
          final int max = (Integer) doc.get("max");
          return min + (int) (Math.random() * (max - min + 1));
        } else if (type.equals("str")) {
          final int len = (Integer) doc.get("len");
          final StringBuilder sb = new StringBuilder(len);
          final byte min = 0x61;
          final byte max = 0x7a;
          for (int i = 0; i < len; ++i) {
            final char c = (char) (min + (byte) (Math.random() * (max - min + 1)));
            sb.append(c);
          }
          return sb.toString();
        }
      }
      final Object val = doc.get(field);
      if (val instanceof BasicDBObject) {
        final BasicDBObject subdoc = (BasicDBObject) val;
        final Object res = handleSpecialFields(subdoc);
        if (res != null) {
          doc.put(field, res);
        }
      } else if (val instanceof BasicDBList) {
        final BasicDBList sublist = (BasicDBList) val;
        handleSpecialFields(sublist);
      }
    }
    return null;
  }

  public void geoNear(final ButtonBase<?, ?> button) {
    final DBObject cmd = new BasicDBObject("geoNear", getCollectionNode().getCollection().getName());
    final DBObject origin = ((DocBuilderField) getBoundUnit(Item.gnOrigin)).getDBObject();
    cmd.put("near", origin);
    final double distance = getDoubleFieldValue(Item.gnMaxDistance);
    cmd.put("maxDistance", distance);
    final int limit = getIntFieldValue(Item.gnLimit);
    cmd.put("limit", limit);
    final double distanceMult = getDoubleFieldValue(Item.gnDistanceMultiplier);
    if (distanceMult > 0) {
      cmd.put("distanceMultiplier", distanceMult);
    }
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.gnQuery)).getDBObject();
    if (query != null) {
      cmd.put("query", query);
    }
    final boolean spherical = getBooleanFieldValue(Item.gnSpherical);
    if (spherical) {
      cmd.put("spherical", true);
    }
    final DBObject search = ((DocBuilderField) getBoundUnit(Item.gnSearch)).getDBObject();
    if (search != null) {
      cmd.put("search", search);
    }

    new DbJobCmd(getCollectionNode().getDbNode().getDb(), cmd).addJob();
  }

  public void fixCollection(final ButtonBase<?, ?> button) {
    final MongoClient m = getCollectionNode().getDbNode().getMongoNode().getMongoClient();
    final ArrayList<MongoNode> mongoNodes = UMongo.instance.getMongos();
    final String[] mongonames = new String[mongoNodes.size() - 1];
    final MongoClient[] mongos = new MongoClient[mongonames.length];
    int i = 0;
    for (final MongoNode node : mongoNodes) {
      final MongoClient m2 = node.getMongoClient();
      if (m == m2) {
        continue;
      }
      mongonames[i] = m2.toString();
      mongos[i] = m2;
      ++i;
    }
    final ComboBox src = (ComboBox) getBoundUnit(Item.fcSrcMongo);
    src.items = mongonames;
    src.structureComponent();
    final FormDialog dialog = (FormDialog) getBoundUnit(Item.fcDialog);
    if (!dialog.show()) {
      return;
    }

    final DBCollection dstCol = getCollectionNode().getCollection();
    final MongoClient srcMongo = mongos[src.getIntValue()];
    final boolean upsert = getBooleanFieldValue(Item.fcUpsert);

    final String dbname = dstCol.getDB().getName();
    final String colname = dstCol.getName();
    final DBCollection srcCol = srcMongo.getDB(dbname).getCollection(colname);
    String txt = "About to copy from ";
    txt += srcMongo.getConnectPoint() + "(" + srcCol.count() + ")";
    txt += " to ";
    txt += m.getConnectPoint() + "(" + dstCol.count() + ")";
    if (!new ConfirmDialog(null, "Confirm Fix Collection", null, txt).show()) {
      return;
    }

    new DbJob() {
      @Override
      public Object doRun() {
        final DBCursor cur = srcCol.find();
        int count = 0;
        int dup = 0;
        while (cur.hasNext()) {
          final DBObject obj = cur.next();
          if (upsert) {
            final BasicDBObject id = new BasicDBObject("_id", obj.get("_id"));
            dstCol.update(id, obj, true, false);
          } else {
            try {
              dstCol.insert(obj);
            } catch (final DuplicateKey e) {
              // dup keys are expected here
              ++dup;
            }
          }
          ++count;
        }
        final DBObject res = new BasicDBObject("count", count);
        res.put("dups", dup);
        return res;
      }

      @Override
      public String getNS() {
        return "*";
      }

      @Override
      public String getShortName() {
        return "Fix Collection";
      }
    }.addJob();
  }

  public void fullTextSearch(final ButtonBase<?, ?> button) {
    final String search = getStringFieldValue(Item.ftsSearch);
    final DBObject filter = ((DocBuilderField) getBoundUnit(Item.ftsFilter)).getDBObject();
    final DBObject project = ((DocBuilderField) getBoundUnit(Item.ftsProject)).getDBObject();
    final int limit = getIntFieldValue(Item.ftsLimit);
    final String language = getStringFieldValue(Item.ftsLanguage);

    final BasicDBObject cmd = new BasicDBObject("text", getCollectionNode().getCollection().getName());
    cmd.put("search", search);
    cmd.put("filter", filter);
    cmd.put("project", project);
    cmd.put("limit", limit);
    cmd.put("language", language);

    new DbJobCmd(getCollectionNode().getCollection().getDB(), cmd, null, button).addJob();
  }

  public void aggregate(final ButtonBase<?, ?> button) {
    final AggregateDialog dialog = (AggregateDialog) ((MenuItem<?, ?>) getBoundUnit(Item.aggregate)).getDialog();
    dialog.refreshAggList();

    if (!dialog.show()) {
      return;
    }

    final BasicDBObject cmd = dialog.getAggregateCommand(getCollectionNode().getCollection().getName());
    new DbJobCmd(getCollectionNode().getCollection().getDB(), cmd, null, button).addJob();
  }

  public void settings(final ButtonBase<?, ?> button) {
    final FormDialog dialog = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.settings)).getDialog();
    final BasicDBObject stats = getStats();
    final boolean pwr2 = stats.getBoolean("userFlags", false);

    setBooleanFieldValue(Item.usePowerOf2Sizes, pwr2);
    if (!dialog.show()) {
      return;
    }

    final boolean newPwr2 = getBooleanFieldValue(Item.usePowerOf2Sizes);
    if (newPwr2 != pwr2) {
      final BasicDBObject cmd = new BasicDBObject("collMod", getCollectionNode().getCollection().getName());
      cmd.put("usePowerOf2Sizes", newPwr2);
      new DbJobCmd(getCollectionNode().getCollection().getDB(), cmd).addJob();
    }
  }

  void refreshTagRangeList() {
    final String ns = getCollectionNode().getCollection().getFullName();
    final ListArea list = (ListArea) getBoundUnit(Item.tagRangeList);
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");
    final DBCollection col = config.getCollection("tags");
    final DBCursor cur = col.find(new BasicDBObject("ns", ns));

    final ArrayList<String> ranges = new ArrayList<String>();
    while (cur.hasNext()) {
      final BasicDBObject range = (BasicDBObject) cur.next();
      ranges.add(MongoUtils.getJSON(range));
    }
    list.items = ranges.toArray(new String[ranges.size()]);
    list.structureComponent();
  }

  public void manageTagRanges(final ButtonBase<?, ?> button) {
    final FormDialog dialog = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.manageTagRanges)).getDialog();
    refreshTagRangeList();
    dialog.show();
  }

  public void addTagRange(final ButtonBase<?, ?> button) {
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");
    final DBCollection col = config.getCollection("tags");
    final String ns = getCollectionNode().getCollection().getFullName();

    final TagRangeDialog dia = (TagRangeDialog) getBoundUnit(Item.tagRangeDialog);
    dia.resetForNew(config, ns);
    if (!dia.show()) {
      return;
    }

    final BasicDBObject doc = dia.getRange(ns);

    new DbJob() {

      @Override
      public Object doRun() {
        return col.insert(doc);
      }

      @Override
      public String getNS() {
        return ns;
      }

      @Override
      public String getShortName() {
        return "Add Tag Range";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject root = new BasicDBObject("doc", doc);
        return root;
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        refreshTagRangeList();
      }
    }.addJob();
  }

  public void editTagRange(final ButtonBase<?, ?> button) {
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");
    final DBCollection col = config.getCollection("tags");
    final String ns = getCollectionNode().getCollection().getFullName();

    final TagRangeDialog dia = (TagRangeDialog) getBoundUnit(Item.tagRangeDialog);
    final String value = getComponentStringFieldValue(Item.tagRangeList);
    final BasicDBObject range = (BasicDBObject) JSON.parse(value);
    dia.resetForEdit(config, range);
    if (!dia.show()) {
      return;
    }

    final BasicDBObject doc = dia.getRange(ns);

    new DbJob() {

      @Override
      public Object doRun() {
        return col.save(doc);
      }

      @Override
      public String getNS() {
        return ns;
      }

      @Override
      public String getShortName() {
        return "Edit Tag Range";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject root = new BasicDBObject("doc", doc);
        return root;
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        refreshTagRangeList();
      }
    }.addJob();
  }

  public void removeTagRange(final ButtonBase<?, ?> button) {
    final DB config = getCollectionNode().getCollection().getDB().getSisterDB("config");
    final DBCollection col = config.getCollection("tags");

    final String ns = getCollectionNode().getCollection().getFullName();
    final String value = getComponentStringFieldValue(Item.tagRangeList);
    final BasicDBObject range = (BasicDBObject) JSON.parse(value);
    final DBObject min = (DBObject) range.get("min");

    final DBObject doc = new BasicDBObject("_id", new BasicDBObject("ns", ns).append("min", min));

    new DbJob() {

      @Override
      public Object doRun() {
        return col.remove(doc);
      }

      @Override
      public String getNS() {
        return ns;
      }

      @Override
      public String getShortName() {
        return "Remove Tag Range";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject root = new BasicDBObject("doc", doc);
        return root;
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        refreshTagRangeList();
      }
    }.addJob();
  }

  public void summarizeData(final ButtonBase<?, ?> button) {
    final CollectionNode cnode = getCollectionNode();

    new DbJob() {
      @Override
      public Object doRun() throws IOException {
        return cnode.summarizeData();
      }

      @Override
      public String getNS() {
        return cnode.getCollection().getFullName();
      }

      @Override
      public String getShortName() {
        return "Summarize Data";
      }
    }.addJob();

  }
}
