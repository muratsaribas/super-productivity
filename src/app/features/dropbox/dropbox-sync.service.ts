import {Injectable} from '@angular/core';
import {GlobalConfigService} from '../config/global-config.service';
import {combineLatest, Observable} from 'rxjs';
import {DropboxSyncConfig} from '../config/global-config.model';
import {concatMap, first, map, mapTo, take, tap} from 'rxjs/operators';
import {DropboxApiService} from './dropbox-api.service';
import {DROPBOX_SYNC_FILE_PATH} from './dropbox.const';
import {AppDataComplete} from '../../imex/sync/sync.model';
import {GlobalSyncService} from '../../core/global-sync/global-sync.service';
import {DataInitService} from '../../core/data-init/data-init.service';
import {LS_DROPBOX_LAST_LOCAL_REVISION, LS_DROPBOX_LOCAL_LAST_SYNC} from '../../core/persistence/ls-keys.const';
import {DropboxFileMetadata} from './dropbox.model';
import {SyncService} from '../../imex/sync/sync.service';

@Injectable({
  providedIn: 'root'
})
export class DropboxSyncService {
  dropboxCfg$: Observable<DropboxSyncConfig> = this._globalConfigService.cfg$.pipe(
    map(cfg => cfg.dropboxSync)
  );
  isEnabled$: Observable<boolean> = this.dropboxCfg$.pipe(
    map(cfg => cfg && cfg.isEnabled),
  );
  syncInterval$: Observable<number> = this.dropboxCfg$.pipe(
    map(cfg => cfg && cfg.syncInterval),
    // TODO remove
    mapTo(10000),
  );

  F: any;

  private _isReady$ = this._dataInitService.isAllDataLoadedInitially$.pipe(
    concatMap(() => combineLatest([
      this.isEnabled$,
      this._dropboxApiService.isReady$,
    ]).pipe(
      map((isEnabled, isReady) => isEnabled && isReady),
      tap((isReady) => !isReady && new Error('Dropbox Sync not ready')),
      first(),
    )),
  );

  constructor(
    private _globalConfigService: GlobalConfigService,
    private _syncService: SyncService,
    private _globalSyncService: GlobalSyncService,
    private _dropboxApiService: DropboxApiService,
    private _dataInitService: DataInitService,
  ) {
    this.F = 'YYYY-MM-DDTHH:mm:SSZ';

    this.sync();
  }

  async sync() {
    await this._isReady$.toPromise();
    const {rev, clientUpdate} = await this._getRevAndLastClientUpdate();

    const d = await this._globalSyncService.inMemory$.pipe(take(1)).toPromise();
    console.log(d);

    const r2 = await this._uploadAppData(d);
    console.log(r2);

  }

  private async _importData(data: AppDataComplete, rev: string) {
    if (!data) {
      const r = (await this._downloadAppData());
      data = r.data;
      rev = r.meta.rev;
    }
    if (!rev) {
      throw new Error('No rev given');
    }

    await this._syncService.importCompleteSyncData(data);
    this._updateLocalRev(rev);
  }

  // NOTE: this does not include milliseconds, which could lead to uncool edge cases... :(
  private async _getRevAndLastClientUpdate(): Promise<{ rev: string; clientUpdate: number }> {
    const r = await this._dropboxApiService.getMetaData(DROPBOX_SYNC_FILE_PATH);
    const d = new Date(r.client_modified);
    return {
      clientUpdate: d.getTime(),
      rev: r.rev,
    };
  }

  private _downloadAppData(): Promise<{ meta: DropboxFileMetadata, data: AppDataComplete }> {
    return this._dropboxApiService.download<AppDataComplete>({
      path: DROPBOX_SYNC_FILE_PATH,
      localRev: this._getLocalRev(),
    });
  }

  private async _uploadAppData(data: AppDataComplete): Promise<DropboxFileMetadata> {
    const r = await this._dropboxApiService.upload({
      path: DROPBOX_SYNC_FILE_PATH,
      data,
      clientModified: data.lastLocalSyncModelChange,
    });
    this._updateLocalRev(r.rev);
    return r;
  }


  // LS HELPER
  // ---------
  private _getLocalRev(): string {
    return localStorage.getItem(LS_DROPBOX_LAST_LOCAL_REVISION);
  }

  private _updateLocalRev(rev: string) {
    if (!rev) {
      throw new Error('No rev given');
    }

    return localStorage.setItem(LS_DROPBOX_LAST_LOCAL_REVISION, rev);
  }

  private _getLocalLastSync(): number {
    return +localStorage.getItem(LS_DROPBOX_LOCAL_LAST_SYNC);
  }

  private _updateLocalLastSync(localLastSync: number) {
    if (typeof localLastSync !== 'number') {
      throw new Error('No correct localLastSync given');
    }
    return localStorage.setItem(LS_DROPBOX_LOCAL_LAST_SYNC, localLastSync.toString());
  }

}
