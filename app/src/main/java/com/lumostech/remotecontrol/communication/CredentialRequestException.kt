package com.lumostech.remotecontrol.communication

class CredentialRequestException(val userMessage: String, val diagnosticCode: String) :
    IllegalStateException("Credential request failed ($diagnosticCode)")
