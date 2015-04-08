// Copyright (c) 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef COMPONENTS_BROWSER_WATCHER_WATCHER_MAIN_API_WIN_H_
#define COMPONENTS_BROWSER_WATCHER_WATCHER_MAIN_API_WIN_H_

#include "base/files/file_path.h"
#include "base/strings/string16.h"

namespace browser_watcher {

// The name of the watcher DLL.
extern const base::FilePath::CharType kWatcherDll[];
// The name of the watcher DLLs entrypoint function.
extern const char kWatcherDLLEntrypoint[];

// The type of the watcher DLL's main entry point.
// The |registry_path| parameter is the path under HKCU where the exit
// codes will be written.
typedef int (*WatcherMainFunction)(const base::char16* registry_path);

}  // namespace browser_watcher

#endif  // COMPONENTS_BROWSER_WATCHER_WATCHER_MAIN_API_WIN_H_
