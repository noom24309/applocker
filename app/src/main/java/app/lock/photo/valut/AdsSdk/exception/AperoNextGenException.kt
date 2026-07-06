/*
 * AperoNextGenException.kt
 *
 * Custom exception type for AperoNextGen initialization errors.
 */

package com.apero.nextgen.AdsSdk.exception

/** Thrown when the AperoNextGen SDK fails to initialize. */
class AperoNextGenException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)
