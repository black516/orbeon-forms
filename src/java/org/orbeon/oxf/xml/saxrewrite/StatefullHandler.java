/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml.saxrewrite;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * <!-- StatefullHandler -->
 * Driver for a state machine that response to SAX events.  Just forwards SAX events to a State
 * which in turn returns the next State.
 *
 * @see State
 */
public final class StatefullHandler implements ContentHandler {
    /**
     * <!-- state -->
     * The current state.
     *
     * @see State
     * @see StatefullHandler
     */
    private State state;

    /**
     * <!-- StatefullHandler -->
     *
     * @param initStt
     * @see StatefullHandler
     */
    public StatefullHandler(final State initStt) {
        state = initStt;
    }

    /**
     * <!-- characters -->
     *
     * @see StatefullHandler
     */
    public void characters(final char[] ch, final int strt, final int len)
            throws SAXException {
        state = state.characters(ch, strt, len);
    }

    /**
     * <!-- endDocument -->
     *
     * @see StatefullHandler
     */
    public void endDocument() throws SAXException {
        state = state.endDocument();
    }

    /**
     * <!-- endElement -->
     *
     * @see StatefullHandler
     */
    public void endElement(final String ns, final String lnam, final String qnam)
            throws SAXException {
        state = state.endElement(ns, lnam, qnam);
    }

    /**
     * <!-- endPrefixMapping -->
     *
     * @see StatefullHandler
     */
    public void endPrefixMapping(final String pfx) throws SAXException {
        state = state.endPrefixMapping(pfx);
    }

    /**
     * <!-- ignorableWhitespace -->
     *
     * @see StatefullHandler
     */
    public void ignorableWhitespace(final char[] ch, final int strt, final int len)
            throws SAXException {
        state = state.ignorableWhitespace(ch, strt, len);
    }

    /**
     * <!-- processingInstruction -->
     *
     * @see StatefullHandler
     */
    public void processingInstruction(final String trgt, final String dat)
            throws SAXException {
        state = state.processingInstruction(trgt, dat);
    }

    /**
     * <!-- setDocumentLocator -->
     *
     * @see StatefullHandler
     */
    public void setDocumentLocator(final Locator loc) {
        state = state.setDocumentLocator(loc);
    }

    /**
     * <!-- skippedEntity -->
     *
     * @see StatefullHandler
     */
    public void skippedEntity(final String nam) throws SAXException {
        state = state.skippedEntity(nam);
    }

    /**
     * <!-- startDocument -->
     *
     * @see StatefullHandler
     */
    public void startDocument() throws SAXException {
        state = state.startDocument();
    }

    /**
     * <!-- startElement -->
     *
     * @see StatefullHandler
     */
    public void startElement
            (final String ns, final String lnam, final String qnam, final Attributes atts)
            throws SAXException {
        state = state.startElement(ns, lnam, qnam, atts);
    }

    /**
     * <!-- startPrefixMapping -->
     *
     * @see StatefullHandler
     */
    public void startPrefixMapping(final String pfx, final String uri) throws SAXException {
        state = state.startPrefixMapping(pfx, uri);
    }
}
