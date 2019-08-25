package com.masterjefferson.wfexample.common

/**
 * Simple interface for types that acquire internal resources and need to release them at a later time.
 */
interface Releasable {
  fun release()
}