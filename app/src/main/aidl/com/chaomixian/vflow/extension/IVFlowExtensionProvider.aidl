package com.chaomixian.vflow.extension;

import android.os.Bundle;

interface IVFlowExtensionProvider {
    Bundle getProviderManifest() = 1;

    Bundle executeModule(in Bundle request) = 2;
}
