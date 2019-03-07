/**
 * Copyright © 2017 Ties BV
 *
 * This file is part of Ties.DB project.
 *
 * Ties.DB project is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ties.DB project is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Ties.DB project. If not, see <https://www.gnu.org/licenses/lgpl-3.0>.
 */
package network.tiesdb.coordinator.service.schema;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import network.tiesdb.router.api.TiesRouter.Node;
import network.tiesdb.schema.api.TiesSchema;
import network.tiesdb.schema.api.TiesSchema.Field;
import network.tiesdb.schema.api.TiesSchema.IndexType;
import network.tiesdb.schema.api.TiesSchema.Table;
import network.tiesdb.schema.api.TiesSchema.Tablespace;
import network.tiesdb.service.scope.api.TiesServiceScopeException;

public class TiesServiceSchema {

    private static final Logger LOG = LoggerFactory.getLogger(TiesServiceSchema.class);

    public static class CacheKey {

        private final String tablespace;
        private final String table;

        public CacheKey(String tablespaceName, String tableName) {
            this.tablespace = tablespaceName;
            this.table = tableName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((table == null) ? 0 : table.hashCode());
            result = prime * result + ((tablespace == null) ? 0 : tablespace.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (table == null) {
                if (other.table != null)
                    return false;
            } else if (!table.equals(other.table))
                return false;
            if (tablespace == null) {
                if (other.tablespace != null)
                    return false;
            } else if (!tablespace.equals(other.tablespace))
                return false;
            return true;
        }
    }

    public static class SchemaCache extends ConcurrentHashMap<CacheKey, Set<FieldDescription>> {
        private static final long serialVersionUID = -5958833752316194583L;

        private final Function<CacheKey, Set<FieldDescription>> entryProvider;

        public SchemaCache(Function<CacheKey, Set<FieldDescription>> entryProvider) {
            this.entryProvider = entryProvider;
        }

        public Set<FieldDescription> load(String tablespaceName, String tableName) {
            CacheKey key = new CacheKey(tablespaceName, tableName);
            return computeIfAbsent(key, this::createSchemaCache);
        }

        // TODO synchronize on key to avoid whole cache locking
        private synchronized Set<FieldDescription> createSchemaCache(CacheKey key) {
            Set<FieldDescription> cacheEntry = get(key);
            if (null == cacheEntry) {
                cacheEntry = this.entryProvider.apply(key);
            }
            return cacheEntry;
        }
    }

    public static class DistributionCache extends ConcurrentHashMap<CacheKey, DistributionCache.Entry> {
        private static final long serialVersionUID = -5958833752316194583L;

        private final Function<CacheKey, Entry> entryProvider;

        public DistributionCache(Function<CacheKey, Entry> entryProvider) {
            this.entryProvider = entryProvider;
        }

        private static class Entry {

            private final Set<? extends RangedNode> nodes;
            private final int replicationFactor;

            public Entry(int replicationFactor, Set<? extends RangedNode> nodes) {
                this.nodes = nodes;
                this.replicationFactor = replicationFactor;
            }

            @Override
            public String toString() {
                return "CacheEntry [replicationFactor=" + replicationFactor + ", nodes=" + nodes + "]";
            }

        }

        private Entry load(String tablespaceName, String tableName) {
            CacheKey key = new CacheKey(tablespaceName, tableName);
            return computeIfAbsent(key, this::createTableCache);
        }

        // TODO synchronize on key to avoid whole cache locking
        private synchronized Entry createTableCache(CacheKey key) {
            Entry cacheEntry = get(key);
            if (null == cacheEntry) {
                cacheEntry = this.entryProvider.apply(key);
            }
            return cacheEntry;
        }
    }

    public static class FieldDescription {

        private final String name;
        private final String type;
        private final boolean isPrimaryKey;

        public FieldDescription(String name, String type, boolean isPrimaryKey) {
            this.name = name;
            this.type = type.toLowerCase();
            this.isPrimaryKey = isPrimaryKey;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FieldDescription other = (FieldDescription) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }
    }

    private static interface RangedNode extends Node {

        boolean inRange(BigInteger key);

    }

    private static class SchemaRangedNode implements RangedNode {

        private final String address;
        private final short network;
        private final Map<BigInteger, Set<BigInteger>> rangeMap;

        public SchemaRangedNode(String address, short network, Map<BigInteger, Set<BigInteger>> rangeMap) {
            this.address = address;
            this.network = network;
            this.rangeMap = rangeMap;
        }

        @Override
        public short getNodeNetwork() {
            return network;
        }

        @Override
        public String getAddressString() {
            return address;
        }

        @Override
        public boolean inRange(BigInteger key) {
            for (Entry<BigInteger, Set<BigInteger>> e : rangeMap.entrySet()) {
                if (e.getValue().contains(key.remainder(e.getKey()))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((address == null) ? 0 : address.hashCode());
            result = prime * result + network;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SchemaRangedNode other = (SchemaRangedNode) obj;
            if (address == null) {
                if (other.address != null)
                    return false;
            } else if (!address.equals(other.address))
                return false;
            if (network != other.network)
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "SchemaRangedNode [address=" + address + ", network=" + network + ", rangeMap=" + rangeMap + "]";
        }

    }

    private final TiesSchema schema;
    private final SchemaCache schemaCache = new SchemaCache(this::loadSchemaFields);
    private final DistributionCache tableCache = new DistributionCache(this::loadSchemaTable);

    public TiesServiceSchema(TiesSchema schema) {
        this.schema = schema;
    }

    public Set<FieldDescription> getFields(String tablespaceName, String tableName) {
        return schemaCache.load(tablespaceName, tableName);
    }

    public Set<? extends Node> getNodes(String tablespaceName, String tableName) {
        return getNodes(tablespaceName, tableName, null);
    }

    public Set<? extends Node> getNodes(String tablespaceName, String tableName, byte[] headerHash) {
        DistributionCache.Entry cacheEntry = tableCache.load(tablespaceName, tableName);
        Set<? extends RangedNode> nodes = cacheEntry.nodes;
        if (null == headerHash) {
            return nodes;
        }
        BigInteger headerHashKey = new BigInteger(1, headerHash);
        Set<RangedNode> resultNodes = nodes.stream().filter(node -> node.inRange(headerHashKey)).collect(toSet());
        return resultNodes;
    }

    private static void checkForInvalidModifications(Set<FieldDescription> refList, Set<FieldDescription> conList) {
        Iterator<FieldDescription> refIter = refList.iterator();
        Iterator<FieldDescription> conIter = conList.iterator();
        while (refIter.hasNext() && conIter.hasNext()) {
            FieldDescription ref = refIter.next();
            FieldDescription con = conIter.next();
            if (ref.equals(con) && (ref.isPrimaryKey && !con.isPrimaryKey)) {
                throw new IllegalStateException("Field `" + ref.getName() + "`:" + ref.getType() + " was removed from primary keys");
            } else {
                continue;
            }
        }
        if (refIter.hasNext()) {
            FieldDescription ref = refIter.next();
            throw new IllegalStateException("Field `" + ref.getName() + "`:" + ref.getType() + " was deleted from contract");
        }
    }

    void retryUpdateFailedDescriptors() {
        // NOP
    }

    void garbadgeCleanup() {
        // NOP
    }

    void updateAllDescriptors() {
        LOG.debug("Start updating schema: {}", schema);
        SchemaCache schemaCacheUpdated = new SchemaCache(this::loadSchemaFields);
        schemaCache.forEach((k, v) -> {
            updateSchemaCache(k, v, schemaCacheUpdated);
        });
        schemaCache.putAll(schemaCacheUpdated);
        DistributionCache tableCacheUpdated = new DistributionCache(this::loadSchemaTable);
        tableCache.keySet().stream().forEach(k -> tableCacheUpdated.load(k.tablespace, k.table));
        tableCache.putAll(tableCacheUpdated);
        LOG.debug("Updating schema finished for: {}", schema);
    }

    private void updateSchemaCache(CacheKey cacheKey, Set<FieldDescription> cachedDescriptions, SchemaCache schemaCacheUpdated) {
        LOG.debug("Start updating: `{}`.`{}`", cacheKey.tablespace, cacheKey.table);
        try {
            Set<FieldDescription> contractDescriptions = loadSchemaFields(cacheKey);
            try {
                checkForInvalidModifications(cachedDescriptions, contractDescriptions);
            } catch (Throwable e) {
                throw new TiesServiceScopeException(
                        "Illegal schema `" + cacheKey.tablespace + "`.`" + cacheKey.table + "` modification detected", e);
            }
            if (cachedDescriptions.equals(contractDescriptions)) {
                LOG.debug("Update succeeded with no changes for: `{}`.`{}`", cacheKey.tablespace, cacheKey.table);
            } else {
                schemaCacheUpdated.put(cacheKey, contractDescriptions);
                LOG.debug("Update succeeded for: `{}`.`{}`", cacheKey.tablespace, cacheKey.table);
            }
        } catch (Throwable e) {
            LOG.error("Update failed for: `{}`.`{}`", cacheKey.tablespace, cacheKey.table, e);
        }
    }

    private Set<FieldDescription> loadSchemaFields(CacheKey key) {
        HashSet<FieldDescription> descriptions = new HashSet<>();
        Tablespace ts = schema.getTablespace(key.tablespace);
        requireNonNull(ts, "Tablespace not found");
        Table t = ts.getTable(key.table);
        requireNonNull(ts, "Table not found");

        t.getIndexes().forEach(idx -> {
            if (IndexType.PRIMARY.equals(idx.getType())) {
                idx.getFields().forEach(fld -> {
                    descriptions.add(new FieldDescription(fld.getName(), fld.getType(), true));
                });
            }
        });

        t.getFieldNames().forEach(fn -> {
            Field f = t.getField(fn);
            descriptions.add(new FieldDescription(fn, f.getType(), false));
        });

        return descriptions;
    }

    private DistributionCache.Entry loadSchemaTable(CacheKey key) {
        { // TODO FIXME Move SingleDebugNode logic to new subclass in TEST environment
            String sna = System.getProperty("network.tiesdb.debug.SingleNodeAddress");
            if (null != sna) {
                HashSet<RangedNode> nodeset = new HashSet<>();
                nodeset.add(new RangedNode() {

                    @Override
                    public short getNodeNetwork() {
                        return schema.getSchemaNetwork();
                    }

                    @Override
                    public String getAddressString() {
                        return sna;
                    }

                    @Override
                    public boolean inRange(BigInteger key) {
                        return true;
                    }

                    @Override
                    public String toString() {
                        return "SingleDebugNode [address=" + getAddressString() + ", network=" + getNodeNetwork() + "]";
                    }

                });
                return new DistributionCache.Entry(1, nodeset);
            }
        }
        Tablespace tablespace = schema.getTablespace(key.tablespace);
        Table table = tablespace.getTable(key.table);
        Set<SchemaRangedNode> nodes = table.getNodeAddresses().stream().map(address -> new SchemaRangedNode(//
                address, //
                schema.getSchemaNetwork(), //
                Collections.unmodifiableMap( //
                        table.getNodeRanges(address).stream().collect( //
                                groupingBy(r -> BigInteger.valueOf(r.getBase()), //
                                        mapping(r -> BigInteger.valueOf(r.getIndex()), toSet()))//
                        ) //
                ) //
        )).collect(toSet());
        return new DistributionCache.Entry(table.getReplicationFactor(), nodes);
    }

    public int getReplicationFactor(String tablespaceName, String tableName) {
        DistributionCache.Entry cacheEntry = tableCache.load(tablespaceName, tableName);
        return cacheEntry.replicationFactor;
    }

}
