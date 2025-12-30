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

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveModuleInput extends ModuleRecipe {

    @Option(displayName = "Input name", description = "The name of the input variable to remove from the module")
    String inputName;

    public RemoveModuleInput(@Nullable String moduleName, @Nullable String source, @Nullable String version,
                             String inputName, @Nullable String filePattern) {
        super(moduleName, source, version, filePattern);
        this.inputName = inputName;
    }

    @NullMarked
    @Override
    public String getDisplayName() {
        return "Remove module input";
    }

    @NullMarked
    @Override
    public String getDescription() {
        return "Removes a specified input variable from a OpenTofu module block.";
    }

    @Override
    protected HclVisitor<ExecutionContext> createModuleVisitor() {
        return new HclVisitor<ExecutionContext>() {
            @Override
            public @NonNull Hcl visitBlock(Hcl.@NonNull Block block, ExecutionContext ctx) {
                block = (Hcl.Block) super.visitBlock(block, ctx);

                if (matchesModule(block) && block.getAttribute(inputName) != null) {
                    Hcl.Block modified = removeAttribute(block);
                    doAfterVisit(new SpacesVisitor<>(SpacesStyle.DEFAULT, modified));
                    return modified;
                }

                return block;
            }

            private Hcl.Block removeAttribute(Hcl.Block block) {
                List<BodyContent> newBody = new ArrayList<>();

                for (BodyContent content : block.getBody()) {
                    if (content instanceof Hcl.Attribute) {
                        Hcl.Attribute attribute = (Hcl.Attribute) content;

                        // Omit the attribute with the specified name
                        if (!inputName.equals(attribute.getSimpleName())) {
                            newBody.add(content);
                        }
                    }
                }

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

        return validated;
    }
}
