package org.modelio.app.mcp.server.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.modelio.gproject.core.IGProject;
import org.modelio.metamodel.PredefinedTypes;
import org.modelio.metamodel.diagrams.AbstractDiagram;
import org.modelio.metamodel.diagrams.ClassDiagram;
import org.modelio.metamodel.diagrams.ObjectDiagram;
import org.modelio.metamodel.diagrams.SequenceDiagram;
import org.modelio.metamodel.mmextensions.standard.factory.IStandardModelFactory;
import org.modelio.metamodel.uml.behavior.interactionModel.ExecutionOccurenceSpecification;
import org.modelio.metamodel.uml.behavior.interactionModel.Interaction;
import org.modelio.metamodel.uml.behavior.interactionModel.Lifeline;
import org.modelio.metamodel.uml.behavior.interactionModel.Message;
import org.modelio.metamodel.uml.behavior.interactionModel.MessageSort;
import org.modelio.metamodel.uml.infrastructure.ModelElement;
import org.modelio.metamodel.uml.statik.Association;
import org.modelio.metamodel.uml.statik.AssociationEnd;
import org.modelio.metamodel.uml.statik.Attribute;
import org.modelio.metamodel.uml.statik.AttributeLink;
import org.modelio.metamodel.uml.statik.Classifier;
import org.modelio.metamodel.uml.statik.DataType;
import org.modelio.metamodel.uml.statik.GeneralClass;
import org.modelio.metamodel.uml.statik.Generalization;
import org.modelio.metamodel.uml.statik.Instance;
import org.modelio.metamodel.uml.statik.Link;
import org.modelio.metamodel.uml.statik.LinkEnd;
import org.modelio.metamodel.uml.statik.NameSpace;
import org.modelio.metamodel.uml.statik.Operation;
import org.modelio.metamodel.uml.statik.Package;
import org.modelio.platform.project.services.IProjectService;
import org.modelio.vcore.model.api.MTools;
import org.modelio.vcore.session.api.ICoreSession;
import org.modelio.vcore.session.api.model.IModel;
import org.modelio.vcore.session.api.transactions.ITransaction;
import org.modelio.vcore.smkernel.mapi.MObject;

/**
 * Real {@link ModelBrowser} implementation, backed by the live Modelio
 * kernel session obtained through {@link IProjectService}.
 */
public final class SessionModelBrowser implements ModelBrowser {

    private final IProjectService projectService;

    public SessionModelBrowser(final IProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public List<PackageInfo> listPackages() {
        final ICoreSession session = getSessionOrThrow();
        final Collection<Package> packages = session.getModel().findByClass(Package.class, false);
        final List<PackageInfo> result = new ArrayList<>();
        for (final Package p : packages) {
            final MObject owner = p.getCompositionOwner();
            final String ownerName = (owner instanceof ModelElement) ? ((ModelElement) owner).getName() : null;
            result.add(new PackageInfo(p.getUuid(), p.getName(), ownerName));
        }
        return result;
    }

    @Override
    public ProjectInfo getProject() {
        return new ProjectInfo(getProjectOrThrow().getName());
    }

    @Override
    public List<ElementInfo> listElements(final String metaclass, final String ownerId, final int limit) {
        final ICoreSession session = getSessionOrThrow();
        final Collection<? extends MObject> elements = findByMetaclass(session, metaclass);
        final List<ElementInfo> result = new ArrayList<>();
        for (final MObject element : elements) {
            final MObject owner = element.getCompositionOwner();
            if (ownerId != null && (owner == null || !ownerId.equals(owner.getUuid()))) {
                continue;
            }
            result.add(toInfo(element));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public ElementInfo getElement(final String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: id");
        }
        final MObject object = getSessionOrThrow().getModel().findById(MObject.class, id, IModel.NODELETED);
        if (object == null) {
            throw new IllegalArgumentException("No model element found with id: " + id);
        }
        return toInfo(object);
    }

    @Override
    public ElementInfo createElement(final CreateElementRequest request) {
        final ICoreSession session = getSessionOrThrow();
        final String kind = required(request.kind(), "kind").toLowerCase(Locale.ROOT);
        final IStandardModelFactory factory = MTools.get(session).getModelFactory(IStandardModelFactory.class);
        try (ITransaction transaction = session.getTransactionSupport().createTransaction("MCP create " + kind)) {
            final ModelElement created;
            switch (kind) {
                case "package":
                    created = factory.createPackage(required(request.name(), "name"),
                            requireType(session, request.ownerId(), NameSpace.class, "owner_id"));
                    break;
                case "class":
                    created = factory.createClass(required(request.name(), "name"),
                            requireType(session, request.ownerId(), NameSpace.class, "owner_id"));
                    break;
                case "attribute": {
                    final Classifier owner = requireType(session, request.ownerId(), Classifier.class, "owner_id");
                    final GeneralClass type = request.typeId() == null
                            ? requireType(session, PredefinedTypes.UNDEFINED_UID, DataType.class, "type_id")
                            : requireType(session, request.typeId(), GeneralClass.class, "type_id");
                    created = factory.createAttribute(required(request.name(), "name"), type, owner);
                    break;
                }
                case "operation": {
                    final Classifier owner = requireType(session, request.ownerId(), Classifier.class, "owner_id");
                    created = factory.createOperation(required(request.name(), "name"), owner);
                    break;
                }
                case "association": {
                    final Classifier source = requireType(session, request.ownerId(), Classifier.class, "owner_id");
                    final Classifier target = requireType(session, request.targetId(), Classifier.class, "target_id");
                    final String roleName = request.roleName() == null ? "" : request.roleName();
                    final Association association = createAssociation(factory, request, source, target, roleName);
                    if (request.name() != null && !request.name().isBlank()) {
                        association.setName(request.name());
                    }
                    applyMultiplicities(association, target, request);
                    created = association;
                    break;
                }
                case "generalization": {
                    final NameSpace subType = requireType(session, request.ownerId(), NameSpace.class, "owner_id");
                    final NameSpace superType = requireType(session, request.targetId(), NameSpace.class, "target_id");
                    created = factory.createGeneralization(subType, superType);
                    break;
                }
                case "class_diagram": {
                    final ModelElement context = requireType(session, request.ownerId(), ModelElement.class, "owner_id");
                    created = factory.createClassDiagram(required(request.name(), "name"), context, null);
                    break;
                }
                case "object_diagram": {
                    final ModelElement context = requireType(session, request.ownerId(), ModelElement.class, "owner_id");
                    created = factory.createObjectDiagram(required(request.name(), "name"), context, null);
                    break;
                }
                case "interaction": {
                    final MObject owner = findElement(session, request.ownerId());
                    final Interaction interaction = factory.createInteraction();
                    interaction.setName(required(request.name(), "name"));
                    if (owner instanceof Operation) {
                        interaction.setOwnerOperation((Operation) owner);
                    } else if (owner instanceof NameSpace) {
                        interaction.setOwner((NameSpace) owner);
                    } else {
                        throw new IllegalArgumentException("owner_id must identify a namespace or operation");
                    }
                    created = interaction;
                    break;
                }
                case "sequence_diagram": {
                    final Interaction interaction = requireType(session, request.ownerId(), Interaction.class,
                            "owner_id");
                    final SequenceDiagram diagram = factory.createSequenceDiagram();
                    diagram.setOrigin(interaction);
                    diagram.setName(required(request.name(), "name"));
                    created = diagram;
                    break;
                }
                case "lifeline": {
                    final Interaction interaction = requireType(session, request.ownerId(), Interaction.class,
                            "owner_id");
                    final Lifeline lifeline = factory.createLifeline(required(request.name(), "name"), interaction);
                    if (request.typeId() != null && !request.typeId().isBlank()) {
                        lifeline.setRepresented(requireType(session, request.typeId(), Instance.class, "type_id"));
                    }
                    created = lifeline;
                    break;
                }
                case "message": {
                    final Interaction interaction = requireType(session, request.ownerId(), Interaction.class,
                            "owner_id");
                    final Lifeline source = requireType(session, request.sourceLifelineId(), Lifeline.class,
                            "source_lifeline_id");
                    final Lifeline target = requireType(session, request.targetLifelineId(), Lifeline.class,
                            "target_lifeline_id");
                    final Message message = factory.createMessage(required(request.name(), "name"),
                            parseMessageSort(request.messageSort()));
                    message.setName(required(request.name(), "name"));
                    if (request.operationId() != null && !request.operationId().isBlank()) {
                        message.setInvoked(requireType(session, request.operationId(), Operation.class, "operation_id"));
                    }
                    final ExecutionOccurenceSpecification send = factory.createExecutionOccurenceSpecification();
                    send.setEnclosingInteraction(interaction);
                    send.getCovered().add(source);
                    final ExecutionOccurenceSpecification receive = factory.createExecutionOccurenceSpecification();
                    receive.setEnclosingInteraction(interaction);
                    receive.getCovered().add(target);
                    send.setSentMessage(message);
                    receive.setReceivedMessage(message);
                    final int line = request.lineNumber() == null ? 1 : request.lineNumber();
                    send.setLineNumber(line);
                    receive.setLineNumber(line);
                    created = message;
                    break;
                }
                case "instance": {
                    final Package owner = requireType(session, request.ownerId(), Package.class, "owner_id");
                    final Instance instance = factory.createInstance(required(request.name(), "name"), owner);
                    if (request.typeId() != null && !request.typeId().isBlank()) {
                        instance.setBase(requireType(session, request.typeId(), Classifier.class, "type_id"));
                    }
                    created = instance;
                    break;
                }
                case "link": {
                    final Instance source = requireType(session, request.ownerId(), Instance.class, "owner_id");
                    final Instance target = requireType(session, request.targetId(), Instance.class, "target_id");
                    created = factory.createLink(source, target, request.roleName() == null ? "" : request.roleName());
                    break;
                }
                case "slot": {
                    final Instance instance = requireType(session, request.ownerId(), Instance.class, "owner_id");
                    final Attribute attribute = requireType(session, request.attributeId(), Attribute.class,
                            "attribute_id");
                    final AttributeLink slot = factory.createAttributeLink();
                    slot.setAttributed(instance);
                    slot.setBase(attribute);
                    if (request.value() != null) {
                        slot.setValue(request.value());
                    }
                    created = slot;
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported kind: " + request.kind());
            }
            transaction.commit();
            return toInfo(created);
        }
    }

    @Override
    public ElementInfo renameElement(final String id, final String name) {
        final ICoreSession session = getSessionOrThrow();
        final MObject object = findElement(session, id);
        if (!(object instanceof ModelElement)) {
            throw new IllegalArgumentException("Element cannot be renamed: " + id);
        }
        try (ITransaction transaction = session.getTransactionSupport().createTransaction("MCP rename element")) {
            ((ModelElement) object).setName(required(name, "name"));
            transaction.commit();
            return toInfo(object);
        }
    }

    /** Creates the association with the aggregation kind requested (plain association by default). */
    private static Association createAssociation(final IStandardModelFactory factory, final CreateElementRequest request,
            final Classifier source, final Classifier target, final String roleName) {
        final String aggregation = request.aggregation() == null ? "association"
                : request.aggregation().toLowerCase(Locale.ROOT);
        switch (aggregation) {
            case "association":
                return factory.createAssociation(source, target, roleName);
            case "aggregation":
                return factory.createAggregation(source, target, roleName);
            case "composition":
                return factory.createComposition(source, target, roleName);
            default:
                throw new IllegalArgumentException("Unsupported aggregation: " + request.aggregation());
        }
    }

    /**
     * Applies the optional source/target multiplicities to the two ends of a freshly created binary
     * association. The end whose target is the association's {@code target} classifier gets the
     * target_multiplicity_* values; the opposite end gets the source_multiplicity_* values.
     */
    private static void applyMultiplicities(final Association association, final Classifier target,
            final CreateElementRequest request) {
        AssociationEnd targetEnd = null;
        AssociationEnd sourceEnd = null;
        for (final AssociationEnd end : association.getEnd()) {
            if (target.equals(end.getTarget())) {
                targetEnd = end;
            } else {
                sourceEnd = end;
            }
        }
        if (targetEnd != null) {
            if (isSet(request.targetMultiplicityMin())) {
                targetEnd.setMultiplicityMin(request.targetMultiplicityMin());
            }
            if (isSet(request.targetMultiplicityMax())) {
                targetEnd.setMultiplicityMax(request.targetMultiplicityMax());
            }
        }
        if (sourceEnd != null) {
            if (isSet(request.sourceMultiplicityMin())) {
                sourceEnd.setMultiplicityMin(request.sourceMultiplicityMin());
            }
            if (isSet(request.sourceMultiplicityMax())) {
                sourceEnd.setMultiplicityMax(request.sourceMultiplicityMax());
            }
        }
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isBlank();
    }

    private static Collection<? extends MObject> findByMetaclass(final ICoreSession session, final String metaclass) {
        final String kind = metaclass == null ? "model_element" : metaclass.toLowerCase(Locale.ROOT);
        switch (kind) {
            case "model_element":
            case "all":
                return session.getModel().findByClass(ModelElement.class, true);
            case "package":
                return session.getModel().findByClass(Package.class, false);
            case "class":
                return session.getModel().findByClass(org.modelio.metamodel.uml.statik.Class.class, false);
            case "attribute":
                return session.getModel().findByClass(Attribute.class, false);
            case "operation":
                return session.getModel().findByClass(Operation.class, false);
            case "association":
                return session.getModel().findByClass(Association.class, false);
            case "generalization":
                return session.getModel().findByClass(Generalization.class, false);
            case "instance":
                return session.getModel().findByClass(Instance.class, false);
            case "link":
                return session.getModel().findByClass(Link.class, false);
            case "slot":
                return session.getModel().findByClass(AttributeLink.class, false);
            case "diagram":
                return session.getModel().findByClass(AbstractDiagram.class, true);
            case "class_diagram":
                return session.getModel().findByClass(ClassDiagram.class, false);
            case "object_diagram":
                return session.getModel().findByClass(ObjectDiagram.class, false);
            case "interaction":
                return session.getModel().findByClass(Interaction.class, false);
            case "sequence_diagram":
                return session.getModel().findByClass(SequenceDiagram.class, false);
            case "lifeline":
                return session.getModel().findByClass(Lifeline.class, false);
            case "message":
                return session.getModel().findByClass(Message.class, false);
            default:
                throw new IllegalArgumentException("Unsupported metaclass filter: " + metaclass);
        }
    }

    private static MessageSort parseMessageSort(final String value) {
        if (value == null || value.isBlank()) {
            return MessageSort.SYNCCALL;
        }
        try {
            return MessageSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported message_sort: " + value
                    + ". Expected SYNCCALL, ASYNCCALL, ASYNCSIGNAL, DESTROYMESSAGE, CREATEMESSAGE or RETURNMESSAGE");
        }
    }

    private static MObject findElement(final ICoreSession session, final String id) {
        final MObject object = session.getModel().findById(MObject.class, required(id, "id"), IModel.NODELETED);
        if (object == null) {
            throw new IllegalArgumentException("No model element found with id: " + id);
        }
        return object;
    }

    private static <T extends MObject> T requireType(final ICoreSession session, final String id,
            final java.lang.Class<T> type, final String argument) {
        final MObject object = findElement(session, required(id, argument));
        if (!type.isInstance(object)) {
            throw new IllegalArgumentException(argument + " must identify a " + type.getSimpleName()
                    + ", got " + object.getMClass().getName());
        }
        return type.cast(object);
    }

    private static String required(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private static ElementInfo toInfo(final MObject object) {
        final MObject owner = object.getCompositionOwner();
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("childCount", Integer.toString(object.getCompositionChildren().size()));
        if (object instanceof Attribute) {
            final GeneralClass type = ((Attribute) object).getType();
            if (type != null) {
                properties.put("typeId", type.getUuid());
                properties.put("typeName", type.getName());
            }
        } else if (object instanceof Operation) {
            final Operation operation = (Operation) object;
            if (operation.getOwner() != null) {
                properties.put("ownerClassifierId", operation.getOwner().getUuid());
                properties.put("ownerClassifierName", operation.getOwner().getName());
            }
        } else if (object instanceof Interaction) {
            final Interaction interaction = (Interaction) object;
            if (interaction.getOwnerOperation() != null) {
                properties.put("ownerOperationId", interaction.getOwnerOperation().getUuid());
                properties.put("ownerOperationName", interaction.getOwnerOperation().getName());
            }
        } else if (object instanceof SequenceDiagram) {
            final ModelElement origin = ((SequenceDiagram) object).getOrigin();
            if (origin != null) {
                properties.put("originId", origin.getUuid());
                properties.put("originName", origin.getName());
            }
        } else if (object instanceof Lifeline) {
            final Lifeline lifeline = (Lifeline) object;
            if (lifeline.getOwner() != null) {
                properties.put("interactionId", lifeline.getOwner().getUuid());
                properties.put("interactionName", lifeline.getOwner().getName());
            }
            if (lifeline.getRepresented() != null) {
                properties.put("representedId", lifeline.getRepresented().getUuid());
                properties.put("representedName", lifeline.getRepresented().getName());
            }
        } else if (object instanceof Message) {
            final Message message = (Message) object;
            if (message.getSortOfMessage() != null) {
                properties.put("messageSort", message.getSortOfMessage().name());
            }
            if (message.getInvoked() != null) {
                properties.put("operationId", message.getInvoked().getUuid());
                properties.put("operationName", message.getInvoked().getName());
            }
            if (message.getSendEvent() != null && !message.getSendEvent().getCovered().isEmpty()) {
                properties.put("sourceLifelineId", message.getSendEvent().getCovered().get(0).getUuid());
            }
            if (message.getReceiveEvent() != null && !message.getReceiveEvent().getCovered().isEmpty()) {
                properties.put("targetLifelineId", message.getReceiveEvent().getCovered().get(0).getUuid());
            }
        } else if (object instanceof Association) {
            int index = 0;
            for (final AssociationEnd end : ((Association) object).getEnd()) {
                final String prefix = "end" + index;
                putIfNotBlank(properties, prefix + "Name", end.getName());
                if (end.getAggregation() != null) {
                    properties.put(prefix + "Aggregation", end.getAggregation().getLiteral());
                }
                putIfNotBlank(properties, prefix + "MultiplicityMin", end.getMultiplicityMin());
                putIfNotBlank(properties, prefix + "MultiplicityMax", end.getMultiplicityMax());
                final Classifier target = end.getTarget();
                if (target != null) {
                    properties.put(prefix + "TargetId", target.getUuid());
                    properties.put(prefix + "TargetName", target.getName());
                }
                index++;
            }
        } else if (object instanceof Generalization) {
            final Generalization generalization = (Generalization) object;
            final NameSpace subType = generalization.getSubType();
            if (subType != null) {
                properties.put("subTypeId", subType.getUuid());
                properties.put("subTypeName", subType.getName());
            }
            final NameSpace superType = generalization.getSuperType();
            if (superType != null) {
                properties.put("superTypeId", superType.getUuid());
                properties.put("superTypeName", superType.getName());
            }
        } else if (object instanceof Instance) {
            final Instance instance = (Instance) object;
            final NameSpace base = instance.getBase();
            if (base != null) {
                properties.put("baseId", base.getUuid());
                properties.put("baseName", base.getName());
            }
            for (final AttributeLink slot : instance.getSlot()) {
                final Attribute attribute = slot.getBase();
                final String label = attribute != null ? attribute.getName() : slot.getUuid();
                properties.put("slot." + label, slot.getValue());
            }
        } else if (object instanceof Link) {
            int index = 0;
            for (final LinkEnd end : ((Link) object).getLinkEnd()) {
                final String prefix = "end" + index;
                putIfNotBlank(properties, prefix + "Name", end.getName());
                final Instance source = end.getSource();
                if (source != null) {
                    properties.put(prefix + "SourceId", source.getUuid());
                    properties.put(prefix + "SourceName", source.getName());
                }
                final Instance target = end.getTarget();
                if (target != null) {
                    properties.put(prefix + "TargetId", target.getUuid());
                    properties.put(prefix + "TargetName", target.getName());
                }
                index++;
            }
        } else if (object instanceof AttributeLink) {
            final AttributeLink slot = (AttributeLink) object;
            final Attribute attribute = slot.getBase();
            if (attribute != null) {
                properties.put("attributeId", attribute.getUuid());
                properties.put("attributeName", attribute.getName());
            }
            final Instance instance = slot.getAttributed();
            if (instance != null) {
                properties.put("instanceId", instance.getUuid());
                properties.put("instanceName", instance.getName());
            }
            putIfNotBlank(properties, "value", slot.getValue());
        }
        return new ElementInfo(object.getUuid(), object.getName(), object.getMClass().getName(),
                owner == null ? null : owner.getUuid(), owner == null ? null : owner.getName(), properties);
    }

    private static void putIfNotBlank(final Map<String, String> properties, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private IGProject getProjectOrThrow() {
        final IGProject project = this.projectService.getOpenedProject();
        if (project == null) {
            throw new IllegalStateException("No Modelio project is currently open.");
        }
        return project;
    }

    private ICoreSession getSessionOrThrow() {
        getProjectOrThrow();
        return this.projectService.getSession();
    }

}
