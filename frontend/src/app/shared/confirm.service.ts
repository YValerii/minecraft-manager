import { ChangeDetectionStrategy, Component, Injectable, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
} from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

import { I18nService } from '../i18n/i18n.service';

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatButton, MatDialogActions, MatDialogClose, MatDialogContent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-dialog-content class="message">{{ message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" class="decline" [mat-dialog-close]="false">{{ i18n.t('common.cancel') }}</button>
      <button matButton="filled" type="button" class="accept" [mat-dialog-close]="true" cdkFocusInitial>
        {{ i18n.t('common.confirm') }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .message {
      padding-top: 24px;
    }
  `,
})
export class ConfirmDialog {
  readonly message = inject<string>(MAT_DIALOG_DATA);
  readonly i18n = inject(I18nService);
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  /** Resolves to true only if the person accepts; closing the dialog any other way counts as declining. */
  async ask(message: string): Promise<boolean> {
    const ref = this.dialog.open<ConfirmDialog, string, boolean>(ConfirmDialog, {
      data: message,
      width: 'min(440px, calc(100vw - 32px))',
      role: 'alertdialog',
      ariaLabel: message,
    });
    return (await firstValueFrom(ref.afterClosed())) === true;
  }
}
