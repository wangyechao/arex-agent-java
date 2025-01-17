package io.arex.inst.database.hibernate;

import io.arex.inst.runtime.context.ContextManager;
import io.arex.inst.runtime.context.RepeatedCollectManager;
import io.arex.agent.bootstrap.model.MockResult;
import io.arex.inst.database.common.DatabaseExtractor;
import io.arex.inst.database.common.DatabaseHelper;
import io.arex.inst.extension.MethodInstrumentation;
import io.arex.inst.extension.TypeInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.loader.Loader;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class LoaderInstrumentation extends TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return named("org.hibernate.loader.Loader");
    }

    @Override
    public List<MethodInstrumentation> methodAdvices() {
        return singletonList(new MethodInstrumentation(
                isMethod().and(named("doQuery"))
                        .and(takesArguments(4))
                        .and(takesArgument(0, named("org.hibernate.engine.spi.SharedSessionContractImplementor")))
                        .and(takesArgument(1, named("org.hibernate.engine.spi.QueryParameters")))
                        .and(takesArgument(3, named("org.hibernate.transform.ResultTransformer"))),
                this.getClass().getName() + "$QueryAdvice"));
    }

    @SuppressWarnings("unused")
    public static class QueryAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter(@Advice.This Loader loader,
                                      @Advice.Argument(1) QueryParameters queryParameters,
                                      @Advice.Local("mockResult") MockResult mockResult,
                                      @Advice.Local("extractor") DatabaseExtractor extractor) {
            RepeatedCollectManager.enter();
            if (ContextManager.needRecordOrReplay()) {
                extractor = new DatabaseExtractor(loader.getSQLString(),
                        DatabaseHelper.parseParameter(queryParameters), "query");
                if (ContextManager.needReplay()) {
                    mockResult = extractor.replay();
                }
            }
            return mockResult != null && mockResult.notIgnoreMockResult();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void onExit(@Advice.This Loader loader,
                                  @Advice.Argument(1) QueryParameters queryParameters,
                                  @Advice.Thrown(readOnly = false) Throwable throwable,
                                  @Advice.Return(readOnly = false) List<?> list,
                                  @Advice.Local("mockResult") MockResult mockResult,
                                  @Advice.Local("extractor") DatabaseExtractor extractor) throws HibernateException {
            if (!RepeatedCollectManager.exitAndValidate()) {
                return;
            }

            if (extractor != null) {
                if (mockResult != null && mockResult.notIgnoreMockResult() && list == null) {
                    if (mockResult.getThrowable() != null) {
                        throwable = mockResult.getThrowable();
                    } else {
                        list = (List<?>) mockResult.getResult();
                    }
                    return;
                }
                if (ContextManager.needRecord()) {
                    if (throwable != null) {
                        extractor.record(throwable);
                    } else {
                        extractor.record(list);
                    }
                }
            }
        }
    }
}
