/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.op;

import org.weasis.core.api.Messages;

public class ByteLut {

    public static final ByteLut defaultLUT = new ByteLut(Messages.getString("ByteLut.def"), null, null); //$NON-NLS-1$
    public static final ByteLut grayLUT = new ByteLut(Messages.getString("ByteLut.gray"), ByteLutCollection.grays, //$NON-NLS-1$
        ByteLutCollection.invert(ByteLutCollection.grays));
    private final String name;
    private final byte[][] lutTable;
    private final byte[][] invertedLutTable;

    public ByteLut(String name, byte[][] lutTable, byte[][] invertedLutTable) {
        this.name = name;
        this.lutTable = lutTable;
        this.invertedLutTable = invertedLutTable;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public byte[][] getLutTable() {
        return lutTable;
    }

    public byte[][] getInvertedLutTable() {
        return invertedLutTable;
    }

}
