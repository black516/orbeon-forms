/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsObject;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.10 The send Element
 */
public class XFormsSendAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        // Find submission object
        final String submissionId = actionElement.attributeValue(XFormsConstants.SUBMISSION_QNAME);
        if (submissionId == null)
            throw new OXFException("Missing mandatory submission attribute on xf:send element.");

        final String resolvedSubmissionStaticId;
        {
            // Resolve AVT
            resolvedSubmissionStaticId = actionInterpreter.resolveAVTProvideValue(actionElement, submissionId);
            if (resolvedSubmissionStaticId == null)
                return;
        }

        // Find actual target
        final XFormsObject submission = actionInterpreter.resolveObject(actionElement, resolvedSubmissionStaticId);
        if (submission instanceof XFormsModelSubmission) {
            // Dispatch event to submission object
            final XFormsEvent newEvent = new XFormsSubmitEvent((XFormsEventTarget) submission, eventProperties(actionInterpreter, actionElement));
            Dispatch.dispatchEvent(newEvent);
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."

            final IndentedLogger indentedLogger = actionInterpreter.indentedLogger();
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("xf:send", "submission does not refer to an existing xf:submission element, ignoring action",
                        "submission id", submissionId);
        }
    }
}
