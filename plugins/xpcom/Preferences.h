#ifndef _H_Preferences
#define _H_Preferences
/*
 * Copyright 2009 Google Inc.
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

#include <string>

#include "mozincludes.h"

#include "nsCOMPtr.h"
#include "nsISupports.h"
#include "nsIObserver.h"
#include "nsIPrefBranch2.h"

class HostChannel;
namespace gwt {
  class Value;
}

class Preferences : public nsIObserver {
  NS_DECL_ISUPPORTS
  NS_DECL_NSIOBSERVER
public:
  Preferences();
  virtual ~Preferences();

  /**
   * Add a new rule to the access list preference.
   *
   * @param pattern pattern to add (currently only an exact-match literal)
   * @param exclude true if the pattern should be excluded from matches,
   *     otherwise included
   */
  void addNewRule(const std::string& pattern, bool exclude);

private:
  static void loadAccessList(const char*);

  nsCOMPtr<nsIPrefBranch2> prefs;
};

#endif
