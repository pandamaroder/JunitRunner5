package app.test.preactions;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;
import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;

public class PreActionsExtension implements BeforeAllCallback, BeforeTestExecutionCallback {

    private final List<PreActionHandler> classHandlers = new LinkedList<>();
    private final List<PreActionHandler> methodHandlers = new LinkedList<>();

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        if (isAnnotated(context.getRequiredTestMethod(), PreActions.class)) {
            final PreActions preActions = findAnnotation(context.getRequiredTestMethod(), PreActions.class).orElseThrow();
            for (Class<? extends PreActionHandler> handler : preActions.handlers()) {
                methodHandlers.add(handler.getDeclaredConstructor().newInstance());
            }
            methodHandlers.sort(Comparator.comparing(PreActionHandler::order));
        }
        for (PreActionHandler methodHandler : methodHandlers) {
            methodHandler.execute();
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (isAnnotated(context.getRequiredTestClass(), PreActions.class)) {
            final PreActions preActions = findAnnotation(context.getRequiredTestClass(), PreActions.class).orElseThrow();
            for (Class<? extends PreActionHandler> handler : preActions.handlers()) {
                classHandlers.add(handler.getDeclaredConstructor().newInstance());
            }
            classHandlers.sort(Comparator.comparing(PreActionHandler::order));
        }
        for (PreActionHandler classHandler : classHandlers) {
            classHandler.execute();
        }
    }
}
