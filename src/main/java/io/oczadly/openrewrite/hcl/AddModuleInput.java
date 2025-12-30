package io.oczadly.openrewrite.hcl;

import io.oczadly.openrewrite.hcl.utils.ModuleBlockPredicates;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.format.SpacesVisitor;
import org.openrewrite.hcl.style.SpacesStyle;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.HclLeftPadded;
import org.openrewrite.hcl.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddModuleInput extends ModuleRecipe {

    @Option(displayName = "Input name", description = "The name of the input variable to add to the module")
    String inputName;

    @Option(displayName = "Input value", description = "The value to assign to the input variable")
    String inputValue;

    public AddModuleInput(@Nullable String moduleName,
                          @Nullable String source,
                          @Nullable String version,
                          String inputName,
                          String inputValue,
                          @Nullable String filePattern) {
        super(moduleName, source, version, filePattern);
        this.inputName = inputName;
        this.inputValue = inputValue;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Add module input";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Adds a new input argument to OpenTofu module blocks.";
    }

    @Override
    protected HclVisitor<ExecutionContext> createModuleVisitor() {
        return new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitBlock(Hcl.@NonNull Block block, ExecutionContext ctx) {
                block = (Hcl.Block) super.visitBlock(block, ctx);

                if (matchesModule(block) && block.getAttribute(inputName) == null) {
                    Hcl.Block modified = addAttribute(block);
                    doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                    return modified;
                }

                return block;
            }

            private Hcl.Block addAttribute(Hcl.Block block) {
                List<BodyContent> newBody = new ArrayList<>(block.getBody());

                String indent = ModuleBlockPredicates.detectIndentation(block);
                String quotedValue = "\"" + inputValue + "\"";

                Hcl.Attribute newAttribute = new Hcl.Attribute(
                    Tree.randomId(),
                    Space.format('\n' + indent),
                    Markers.EMPTY,
                    new Hcl.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, inputName),
                    new HclLeftPadded<>(Space.format(" "), Hcl.Attribute.Type.Assignment, Markers.EMPTY),
                    new Hcl.Literal(Tree.randomId(), Space.format(" "), Markers.EMPTY, null, quotedValue),
                    null
                );
                newBody.add(newAttribute);

                return block.withBody(newBody);
            }
        };
    }

    @Override
    public @NonNull Validated<Object> validate() {
        Validated<Object> validated = super.validate();

        if (inputName.trim().isEmpty()) {
            validated = validated.and(Validated.invalid(
                "inputName",
                inputName,
                "'inputName' must be specified and cannot be empty."
            ));
        }

        if (inputValue.trim().isEmpty()) {
            validated = validated.and(Validated.invalid(
                "inputValue",
                inputValue,
                "'inputValue' must be specified and cannot be empty."
            ));
        }

        return validated;
    }
}
