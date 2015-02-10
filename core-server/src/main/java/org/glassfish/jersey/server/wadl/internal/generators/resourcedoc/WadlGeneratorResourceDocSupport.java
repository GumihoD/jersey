/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.server.wadl.internal.generators.resourcedoc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import javax.inject.Provider;
import javax.xml.parsers.SAXParserFactory;

import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.wadl.WadlGenerator;
import org.glassfish.jersey.server.wadl.internal.ApplicationDescription;
import org.glassfish.jersey.server.wadl.internal.WadlUtils;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.ClassDocType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.MethodDocType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.ParamDocType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.RepresentationDocType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.ResourceDocType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.ResponseDocType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.model.WadlParamType;
import org.glassfish.jersey.server.wadl.internal.generators.resourcedoc.xhtml.Elements;

import com.sun.research.ws.wadl.Application;
import com.sun.research.ws.wadl.Doc;
import com.sun.research.ws.wadl.Method;
import com.sun.research.ws.wadl.Param;
import com.sun.research.ws.wadl.ParamStyle;
import com.sun.research.ws.wadl.Representation;
import com.sun.research.ws.wadl.Request;
import com.sun.research.ws.wadl.Resource;
import com.sun.research.ws.wadl.Resources;
import com.sun.research.ws.wadl.Response;

/**
 * A {@link org.glassfish.jersey.server.wadl.WadlGenerator} implementation that enhances the generated wadl by
 * information read from a resourcedoc (containing javadoc information about resource
 * classes).
 * <p>
 * The resourcedoc information can either be provided via a {@link File} ({@link #setResourceDocFile(File)}) reference or
 * via an {@link InputStream} ({@link #setResourceDocStream(InputStream)}).
 * </p>
 * <p>
 * The {@link File} should be used when using the maven-wadl-plugin for generating wadl offline,
 * the {@link InputStream} should be used when the extended wadl is generated by jersey at runtime, e.g.
 * using the {@link org.glassfish.jersey.server.wadl.config.WadlGeneratorConfig} for configuration.
 * </p>
 *
 * @author Martin Grotzke (martin.grotzke at freiheit.com)
 * @author Miroslav Fuksa
 */
public class WadlGeneratorResourceDocSupport implements WadlGenerator {

    private WadlGenerator delegate;
    private File resourceDocFile;
    private InputStream resourceDocStream;
    private ResourceDocAccessor resourceDoc;
    @Context
    private Provider<SAXParserFactory> saxFactoryProvider;


    public WadlGeneratorResourceDocSupport() {
    }

    public WadlGeneratorResourceDocSupport(final WadlGenerator wadlGenerator, final ResourceDocType resourceDoc) {
        delegate = wadlGenerator;
        this.resourceDoc = new ResourceDocAccessor(resourceDoc);
    }

    public void setWadlGeneratorDelegate(final WadlGenerator delegate) {
        this.delegate = delegate;
    }

    /**
     * Set the <code>resourceDocFile</code> to the given file. Invoking this method is only allowed, as long as
     * the <code>resourceDocStream</code> is not set, otherwise an {@link IllegalStateException} will be thrown.
     * @param resourceDocFile the resourcedoc file to set.
     */
    public void setResourceDocFile(final File resourceDocFile) {
        if (resourceDocStream != null) {
            throw new IllegalStateException("The resourceDocStream property is already set," +
                    " therefore you cannot set the resourceDocFile property. Only one of both can be set at a time.");
        }
        this.resourceDocFile = resourceDocFile;
    }

    /**
     * Set the <code>resourceDocStream</code> to the given file. Invoking this method is only allowed, as long as
     * the <code>resourceDocFile</code> is not set, otherwise an {@link IllegalStateException} will be thrown.
     * <p>
     * The resourcedoc stream must be closed by the client providing the stream.
     * </p>
     * @param resourceDocStream the resourcedoc stream to set.
     */
    public void setResourceDocStream(final InputStream resourceDocStream) {
        if (this.resourceDocStream != null) {
            throw new IllegalStateException("The resourceDocFile property is already set," +
                    " therefore you cannot set the resourceDocStream property. Only one of both can be set at a time.");
        }
        this.resourceDocStream = resourceDocStream;
    }

    public void init() throws Exception {
        if (resourceDocFile == null && resourceDocStream == null) {
            throw new IllegalStateException("Neither the resourceDocFile nor the resourceDocStream" +
                    " is set, one of both is required.");
        }
        delegate.init();

        try (final InputStream inputStream = resourceDocFile != null ? new FileInputStream(resourceDocFile) : resourceDocStream) {
            final ResourceDocType resourceDocType =
                    WadlUtils.unmarshall(inputStream, saxFactoryProvider.get(), ResourceDocType.class);
            resourceDoc = new ResourceDocAccessor(resourceDocType);
        } finally {
            resourceDocFile = null;
        }
    }

    public String getRequiredJaxbContextPath() {
        String name = Elements.class.getName();
        name = name.substring(0, name.lastIndexOf('.'));

        return delegate.getRequiredJaxbContextPath() == null
                ? name
                : delegate.getRequiredJaxbContextPath() + ":" + name;
    }

    /**
     * @return the {@link com.sun.research.ws.wadl.Application} created by the delegate.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createApplication()
     */
    public Application createApplication() {
        return delegate.createApplication();
    }

    /**
     * @param r Jersey resource component for which the WADL reource is to be created.
     * @param path path where the resource is exposed.
     * @return the enhanced {@link com.sun.research.ws.wadl.Resource}.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createResource(org.glassfish.jersey.server.model.Resource, String)
     */
    public Resource createResource(final org.glassfish.jersey.server.model.Resource r, final String path) {
        final Resource result = delegate.createResource(r, path);
        for (final Class<?> resourceClass : r.getHandlerClasses()) {
            final ClassDocType classDoc = resourceDoc.getClassDoc(resourceClass);
            if (classDoc != null && !isEmpty(classDoc.getCommentText())) {
                final Doc doc = new Doc();
                doc.getContent().add(classDoc.getCommentText());
                result.getDoc().add(doc);
            }
        }
        return result;
    }

    /**
     * @param resource Jersey resource component.
     * @param resourceMethod resource method.
     * @return the enhanced {@link com.sun.research.ws.wadl.Method}.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createMethod(org.glassfish.jersey.server.model.Resource,
     * org.glassfish.jersey.server.model.ResourceMethod)
     */
    public Method createMethod(final org.glassfish.jersey.server.model.Resource resource,
                               final ResourceMethod resourceMethod) {
        final Method result = delegate.createMethod(resource, resourceMethod);
        final java.lang.reflect.Method method = resourceMethod.getInvocable().getDefinitionMethod();
        final MethodDocType methodDoc = resourceDoc.getMethodDoc(method.getDeclaringClass(), method);
        if (methodDoc != null && !isEmpty(methodDoc.getCommentText())) {
            final Doc doc = new Doc();
            doc.getContent().add(methodDoc.getCommentText());
            // doc.getOtherAttributes().put( new QName( "xmlns" ), "http://www.w3.org/1999/xhtml" );
            result.getDoc().add(doc);
        }

        return result;
    }

    /**
     * @param r Jersey resource component.
     * @param m resource method.
     * @param mediaType media type.
     * @return the enhanced {@link com.sun.research.ws.wadl.Representation}.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createRequestRepresentation(org.glassfish.jersey.server.model.Resource,
     * org.glassfish.jersey.server.model.ResourceMethod, javax.ws.rs.core.MediaType)
     */
    public Representation createRequestRepresentation(final org.glassfish.jersey.server.model.Resource r,
                                                      final org.glassfish.jersey.server.model.ResourceMethod m,
                                                      final MediaType mediaType) {
        final Representation result = delegate.createRequestRepresentation(r, m, mediaType);
        final RepresentationDocType requestRepresentation = resourceDoc.getRequestRepresentation(m.getInvocable()
                        .getDefinitionMethod().getDeclaringClass(),
                m.getInvocable().getDefinitionMethod(), result.getMediaType()
        );
        if (requestRepresentation != null) {
            result.setElement(requestRepresentation.getElement());
            addDocForExample(result.getDoc(), requestRepresentation.getExample());
        }
        return result;
    }

    /**
     * @param r Jersey resource component.
     * @param m resource method.
     * @return the enhanced {@link com.sun.research.ws.wadl.Request}.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createRequest(org.glassfish.jersey.server.model.Resource,
     * org.glassfish.jersey.server.model.ResourceMethod)
     */
    public Request createRequest(final org.glassfish.jersey.server.model.Resource r,
                                 final org.glassfish.jersey.server.model.ResourceMethod m) {
        return delegate.createRequest(r, m);
    }

    /**
     * @param r Jersey resource component.
     * @param m resource method.
     * @return the enhanced {@link com.sun.research.ws.wadl.Response}.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createResponses(org.glassfish.jersey.server.model.Resource,
     * org.glassfish.jersey.server.model.ResourceMethod)
     */
    public List<Response> createResponses(final org.glassfish.jersey.server.model.Resource r,
                                          final org.glassfish.jersey.server.model.ResourceMethod m) {
        final ResponseDocType responseDoc = resourceDoc.getResponse(m.getInvocable().getDefinitionMethod().getDeclaringClass(),
                m.getInvocable().getDefinitionMethod());
        List<Response> responses = new ArrayList<Response>();
        if (responseDoc != null && responseDoc.hasRepresentations()) {
            for (final RepresentationDocType representationDoc : responseDoc.getRepresentations()) {
                final Response response = new Response();

                final Representation wadlRepresentation = new Representation();
                wadlRepresentation.setElement(representationDoc.getElement());
                wadlRepresentation.setMediaType(representationDoc.getMediaType());
                addDocForExample(wadlRepresentation.getDoc(), representationDoc.getExample());
                addDoc(wadlRepresentation.getDoc(), representationDoc.getDoc());

                response.getStatus().add(representationDoc.getStatus());
                response.getRepresentation().add(wadlRepresentation);

                responses.add(response);
            }

            if (!responseDoc.getWadlParams().isEmpty()) {
                for (final WadlParamType wadlParamType : responseDoc.getWadlParams()) {
                    final Param param = new Param();
                    param.setName(wadlParamType.getName());
                    param.setStyle(ParamStyle.fromValue(wadlParamType.getStyle()));
                    param.setType(wadlParamType.getType());
                    addDoc(param.getDoc(), wadlParamType.getDoc());
                    for (final Response response : responses) {
                        response.getParam().add(param);
                    }
                }
            }

            if (!isEmpty(responseDoc.getReturnDoc())) {
                for (final Response response : responses) {
                    addDoc(response.getDoc(), responseDoc.getReturnDoc());
                }
            }

        } else {
            responses = delegate.createResponses(r, m);
        }

        return responses;
    }

    private void addDocForExample(final List<Doc> docs, final String example) {
        if (!isEmpty(example)) {
            final Doc doc = new Doc();

            final Elements pElement = Elements.el("p")
                    .add(Elements.val("h6", "Example"))
                    .add(Elements.el("pre").add(Elements.val("code", example)));

            doc.getContent().add(pElement);
            docs.add(doc);
        }
    }

    private void addDoc(final List<Doc> docs, final String text) {
        if (!isEmpty(text)) {
            final Doc doc = new Doc();
            doc.getContent().add(text);
            docs.add(doc);
        }
    }

    /**
     * @param r Jersey resource component.
     * @param m resource method.
     * @param p method parameter.
     * @return the enhanced {@link Param}.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createParam(org.glassfish.jersey.server.model.Resource,
     * org.glassfish.jersey.server.model.ResourceMethod, org.glassfish.jersey.server.model.Parameter)
     */
    public Param createParam(final org.glassfish.jersey.server.model.Resource r,
                             final org.glassfish.jersey.server.model.ResourceMethod m, final Parameter p) {
        final Param result = delegate.createParam(r, m, p);
        if (result != null) {
            final ParamDocType paramDoc = resourceDoc.getParamDoc(m.getInvocable().getDefinitionMethod().getDeclaringClass(),
                    m.getInvocable().getDefinitionMethod(), p);
            if (paramDoc != null && !isEmpty(paramDoc.getCommentText())) {
                final Doc doc = new Doc();
                doc.getContent().add(paramDoc.getCommentText());
                result.getDoc().add(doc);
            }
        }
        return result;
    }

    /**
     * @return the {@link com.sun.research.ws.wadl.Resources} created by the delegate.
     * @see org.glassfish.jersey.server.wadl.WadlGenerator#createResources()
     */
    public Resources createResources() {
        return delegate.createResources();
    }

    private boolean isEmpty(final String text) {
        return text == null || text.isEmpty() || "".equals(text.trim());
    }

    // ================ methods for post build actions =======================

    @Override
    public ExternalGrammarDefinition createExternalGrammar() {
        return delegate.createExternalGrammar();
    }

    @Override
    public void attachTypes(final ApplicationDescription egd) {
        delegate.attachTypes(egd);
    }

}
