/*
 * Copyright (c) 2008 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pivot.wtk;

import pivot.util.Vote;

/**
 * <p>Sheet state listener interface.</p>
 *
 * @author gbrown
 */
public interface SheetStateListener extends SheetCloseListener {
    /**
     * Called to preview a sheet close event.
     *
     * @param sheet
     * @param result
     */
    public Vote previewSheetClose(Sheet sheet, boolean result);

    /**
     * Called when a sheet close event has been vetoed.
     *
     * @param dialog
     */
    public void sheetCloseVetoed(Sheet sheet, Vote reason);
}
