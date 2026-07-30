package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context.SearchContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Validators {
  private Validators() {}

  public static ValidationProcessor allOf(ValidationProcessor... validators) {
    return new AllOfValidator(validators);
  }

  public static ValidationProcessor allOf(List<ValidationProcessor> validators) {
    return new AllOfValidator(validators);
  }

  public static ValidationProcessor anyOf(ValidationProcessor... validators) {
    return new AnyOfValidator(validators);
  }

  public static ValidationProcessor anyOf(List<ValidationProcessor> validators) {
    return new AnyOfValidator(validators);
  }

  public static ValidationProcessor noneOf(ValidationProcessor... validators) {
    return new NoneOfValidator(validators);
  }

  public static ValidationProcessor noneOf(List<ValidationProcessor> validators) {
    return new NoneOfValidator(validators);
  }

  public static ValidationProcessor not(ValidationProcessor validator) {
    return new NotValidator(Objects.requireNonNull(validator, "validator must not be null"));
  }

  public static ValidationProcessor alwaysTrue() {
    return AlwaysTrueValidator.INSTANCE;
  }

  public static ValidationProcessor alwaysFalse() {
    return AlwaysFalseValidator.INSTANCE;
  }

  private static List<ValidationProcessor> requireAllNonNull(ValidationProcessor... validators) {
    Objects.requireNonNull(validators, "validators must not be null");
    return requireAllNonNull(Arrays.asList(validators));
  }

  private static List<ValidationProcessor> requireAllNonNull(List<ValidationProcessor> validators) {
    Objects.requireNonNull(validators, "validators must not be null");
    if (validators.isEmpty()) {
      return Collections.emptyList();
    }
    List<ValidationProcessor> list = new ArrayList<>(validators.size());
    for (int i = 0; i < validators.size(); i++) {
      list.add(Objects.requireNonNull(validators.get(i), "validators[" + i + "] must not be null"));
    }
    return list;
  }

  private abstract static class AbstractCompositeValidator implements ValidationProcessor {
    protected final List<ValidationProcessor> children;

    protected AbstractCompositeValidator(ValidationProcessor... validators) {
      this.children = requireAllNonNull(validators);
    }

    protected AbstractCompositeValidator(List<ValidationProcessor> validators) {
      this.children = requireAllNonNull(validators);
    }

    @Override
    public void initializeSearch(SearchContext searchContext) {
      for (ValidationProcessor child : children) {
        child.initializeSearch(searchContext);
      }
    }

    @Override
    public void finalizeSearch(SearchContext searchContext) {
      for (ValidationProcessor child : children) {
        child.finalizeSearch(searchContext);
      }
    }
  }

  private static class AllOfValidator extends AbstractCompositeValidator {
    public AllOfValidator(ValidationProcessor... validators) {
      super(validators);
    }

    public AllOfValidator(List<ValidationProcessor> validators) {
      super(validators);
    }

    @Override
    public boolean isValid(EvaluationContext context) {
      for (ValidationProcessor child : children) {
        if (!child.isValid(context)) {
          return false;
        }
      }
      return true;
    }
  }

  private static class AnyOfValidator extends AbstractCompositeValidator {
    public AnyOfValidator(ValidationProcessor... validators) {
      super(validators);
    }

    public AnyOfValidator(List<ValidationProcessor> validators) {
      super(validators);
    }

    @Override
    public boolean isValid(EvaluationContext context) {
      if (children.isEmpty()) {
        return false;
      }
      for (ValidationProcessor child : children) {
        if (child.isValid(context)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class NoneOfValidator extends AbstractCompositeValidator {
    public NoneOfValidator(ValidationProcessor... validators) {
      super(validators);
    }

    public NoneOfValidator(List<ValidationProcessor> validators) {
      super(validators);
    }

    @Override
    public boolean isValid(EvaluationContext context) {
      for (ValidationProcessor child : children) {
        if (child.isValid(context)) {
          return false;
        }
      }
      return true;
    }
  }

  private static class NotValidator implements ValidationProcessor {
    private final ValidationProcessor child;

    public NotValidator(ValidationProcessor validator) {
      this.child = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public void initializeSearch(SearchContext searchContext) {
      child.initializeSearch(searchContext);
    }

    @Override
    public boolean isValid(EvaluationContext context) {
      return !child.isValid(context);
    }

    @Override
    public void finalizeSearch(SearchContext searchContext) {
      child.finalizeSearch(searchContext);
    }
  }

  private static class AlwaysTrueValidator implements ValidationProcessor {
    public static final AlwaysTrueValidator INSTANCE = new AlwaysTrueValidator();

    private AlwaysTrueValidator() {}

    @Override
    public boolean isValid(EvaluationContext context) {
      return true;
    }
  }

  private static class AlwaysFalseValidator implements ValidationProcessor {
    public static final AlwaysFalseValidator INSTANCE = new AlwaysFalseValidator();

    private AlwaysFalseValidator() {}

    @Override
    public boolean isValid(EvaluationContext context) {
      return false;
    }
  }
}
