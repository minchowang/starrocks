package com.starrocks.pseudocluster;

import com.starrocks.common.AlreadyExistsException;
import com.starrocks.common.UserException;
import com.starrocks.thrift.TFinishTaskRequest;
import com.starrocks.thrift.TPartitionVersionInfo;
import com.starrocks.thrift.TTabletVersionPair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeTxnManager {
    private static final Logger LOG = LogManager.getLogger(BeTxnManager.class);

    static class TxnTabletInfo {
        long tabletId;
        Rowset rowset;

        public TxnTabletInfo(long tabletId) {
            this.tabletId = tabletId;
        }
    }

    static class TxnInfo {
        long txnId;
        Map<Long, Map<Long, TxnTabletInfo>> partitions;

        TxnInfo(long txnId) {
            this.txnId = txnId;
            partitions = new HashMap<>();
        }
    }

    PseudoBackend backend;
    Map<Long, TxnInfo> txns = new HashMap<>();

    BeTxnManager(PseudoBackend backend) {
        this.backend = backend;
    }

    public synchronized void commit(long txnId, long partitionId, Tablet tablet, Rowset rowset) throws UserException {
        TxnInfo tinfo = txns.computeIfAbsent(txnId, k -> new TxnInfo(k));
        Map<Long, TxnTabletInfo> tablets = tinfo.partitions.computeIfAbsent(partitionId, k -> new HashMap<>());
        TxnTabletInfo tabletInfo = tablets.get(tablet.id);
        if (tabletInfo != null) {
            throw new AlreadyExistsException("txn:" + txnId + " tablet:" + tablet.id + " already exists");
        }
        tabletInfo = tablets.computeIfAbsent(tablet.id, id -> new TxnTabletInfo(id));
        tabletInfo.rowset = rowset;
    }

    public synchronized void publish(long txnId, List<TPartitionVersionInfo> partitions, TFinishTaskRequest finish) {
        TxnInfo tinfo = txns.get(txnId);
        Exception e = null;
        int totalTablets = 0;
        List<Long> errorTabletIds = new ArrayList<>();
        List<TTabletVersionPair> tabletVersions = new ArrayList<>();
        for (TPartitionVersionInfo pInfo : partitions) {
            if (tinfo == null) {
                List<Tablet> tabletsInPartition = backend.getTabletManager().getTablets(pInfo.partition_id);
                totalTablets += tabletsInPartition.size();
                for (Tablet tablet : tabletsInPartition) {
                    TTabletVersionPair p = new TTabletVersionPair();
                    p.tablet_id = tablet.id;
                    p.version = tablet.max_continuous_version();
                    tabletVersions.add(p);
                }
                continue;
            }
            Map<Long, TxnTabletInfo> tablets = tinfo.partitions.get(pInfo.partition_id);
            if (tablets == null) {
                LOG.warn("publish version txn:" + txnId + " partition:" + pInfo.partition_id + " not found");
                continue;
            }
            for (TxnTabletInfo tabletInfo : tablets.values()) {
                totalTablets++;
                Tablet tablet = backend.getTabletManager().getTablet(tabletInfo.tabletId);
                if (tablet == null) {
                    errorTabletIds.add(tablet.id);
                    if (e == null) {
                        e = new UserException(
                                "publish version failed txn:" + txnId + " partition:" + pInfo.partition_id + " tablet:" +
                                        tablet.id + " not found");
                    }
                } else {
                    try {
                        tablet.commitRowset(tabletInfo.rowset, pInfo.version);
                    } catch (Exception ex) {
                        errorTabletIds.add(tablet.id);
                        e = ex;
                    }
                    TTabletVersionPair p = new TTabletVersionPair();
                    p.tablet_id = tablet.id;
                    p.version = tablet.max_continuous_version();
                    tabletVersions.add(p);
                }
            }
        }
        LOG.info("backend: {} txn: {} publish version error:{} / total:{} {}", backend.be.getId(), txnId, errorTabletIds.size(),
                totalTablets,
                e == null ? "" : e.getMessage());
        finish.setError_tablet_ids(errorTabletIds);
        finish.setTablet_versions(tabletVersions);
        if (e != null) {
            finish.setTask_status(PseudoBackend.toStatus(e));
        }
    }

}
