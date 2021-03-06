package pgo.trans.passes.validation;

import pgo.Unreachable;
import pgo.errors.IssueContext;
import pgo.model.mpcal.ModularPlusCalYield;
import pgo.model.pcal.*;
import pgo.model.tla.TLAExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Validates the labeling rules of a Modular PlusCal specification. The labeling rules
 * used are exactly the same as those used by standard PlusCal and are described in
 * its manual.
 */
public class ModularPlusCalLabelingRulesVisitor extends PlusCalStatementVisitor<Void, RuntimeException> {

    /**
     * Validates whether the next statement following a certain subtree in the AST requires to a label.
     * This visitor assumes the root node of the AST is an If or Either expression (checks need to be
     * performed by caller).
     *
     * The statement succeeding an 'if' or 'either' PlusCal statement needs to be labeled if there is:
     *   - a label
     *   - 'return'
     *   - 'goto'
     *   - 'call'
     *
     * anywhere within the 'if' or 'either' statement.
     */
    private final class IfEitherNextStatementRequiresLabel extends PlusCalStatementVisitor<Boolean, RuntimeException> {
        public Boolean visit(PlusCalLabeledStatements plusCalLabeledStatements) {
            return true;
        }

        public Boolean visit(PlusCalWhile plusCalWhile) {
            for (PlusCalStatement statement : plusCalWhile.getBody()) {
                if (statement.accept(this)) {
                    return true;
                }
            }

            return false;
        }

        public Boolean visit(PlusCalIf plusCalIf) {
            for (PlusCalStatement statement : plusCalIf.getYes()) {
                if (statement.accept(this)) {
                    return true;
                }
            }

            for (PlusCalStatement statement : plusCalIf.getNo()) {
                if (statement.accept(this)) {
                    return true;
                }
            }

            return false;
        }

        public Boolean visit(PlusCalEither plusCalEither){
            for (List<PlusCalStatement> cases : plusCalEither.getCases()) {
                for (PlusCalStatement statement : cases) {
                    if (statement.accept(this)) {
                        return true;
                    }
                }
            }

            return false;
        }

        public Boolean visit(PlusCalAssignment plusCalAssignment) {
            return false;
        }

        public Boolean visit(PlusCalReturn plusCalReturn) {
            return true;
        }

        public Boolean visit(PlusCalSkip plusCalSkip) {
            return false;
        }

        public Boolean visit(PlusCalCall plusCalCall) {
            return true;
        }

        public Boolean visit(PlusCalMacroCall macroCall) {
            throw new Unreachable();
        }

        public Boolean visit(PlusCalWith plusCalWith) {
            for (PlusCalStatement statement : plusCalWith.getBody()) {
                if (statement.accept(this)) {
                    return true;
                }
            }

            return false;
        }

        public Boolean visit(PlusCalPrint plusCalPrint) {
            return false;
        }

        public Boolean visit(PlusCalAssert plusCalAssert) {
            return false;
        }

        public Boolean visit(PlusCalAwait plusCalAwait) {
            return false;
        }

        public Boolean visit(PlusCalGoto plusCalGoto) {
            return true;
        }

        public Boolean visit(ModularPlusCalYield modularPlusCalYield) {
            throw new Unreachable();
        }
    }

    // Some label names are reserved by the PlusCal to TLA+ translator
    private static String[] RESERVED_LABELS = {"Done", "Error"};

    private IssueContext ctx;
    private PlusCalStatement previousStatement;
    private boolean labelsAllowed;
    private Set<TLAExpression> assignedVariables;

    public ModularPlusCalLabelingRulesVisitor(IssueContext ctx) {
        this.ctx = ctx;
        this.previousStatement = null;
        this.labelsAllowed = true;
        this.assignedVariables = new HashSet<>();
    }

    public ModularPlusCalLabelingRulesVisitor(IssueContext ctx, boolean labelsAllowed) {
        this.ctx = ctx;
        this.previousStatement = null;
        this.labelsAllowed = labelsAllowed;
        this.assignedVariables = new HashSet<>();
    }

    public Void visit(PlusCalLabeledStatements plusCalLabeledStatements) {
        this.previousStatement = plusCalLabeledStatements;

        if (!labelsAllowed) {
            labelNotAllowed(plusCalLabeledStatements);
        } else if (isReserved(plusCalLabeledStatements.getLabel())) {
            reservedLabelName(plusCalLabeledStatements);
        }

        // erase context of assigned variables when starting a new label
        this.assignedVariables = new HashSet<>();

        for (PlusCalStatement statement : plusCalLabeledStatements.getStatements()) {
            statement.accept(this);
        }

        return null;
    }

    // NOTE: if a variable is assigned inside the body of a `while` loop,
    // that assignment is "forgotten" once control flow exits the loop. That
    // is because technically, the body of a `while` loop executes either once
    // or not at all when its label is scheduled.
    //
    // Double assignments *within* the body of a `while` loop are still not
    // allowed and enforced by this check; however, the `assignedVariables` set
    // is reset to the empty set after visiting the `while` loop.
    public Void visit(PlusCalWhile plusCalWhile) {
        if (!firstOrLabeled(previousStatement)) {
            missingLabel(plusCalWhile);
        }

        this.previousStatement = plusCalWhile;
        this.assignedVariables = new HashSet<>();
        for (PlusCalStatement statement : plusCalWhile.getBody()) {
            statement.accept(this);
        }

        this.assignedVariables = new HashSet<>();
        this.previousStatement = plusCalWhile;
        return null;
    }

    public Void visit(PlusCalIf plusCalIf) {
        checkProcedureCall(plusCalIf);
        checkReturnOrGoto(plusCalIf);
        checkIfEither(plusCalIf);

        // save the actual statement preceding this 'if' statement so that
        // both the 'yes' and 'no' branches of this condition have a consistent
        // view of the previous statement.
        PlusCalStatement oldPreviousStatement = previousStatement;

        // maintain a copy of the old set of assigned variables (variables
        // assigned in different branches of the if condition should not
        // be considered an error)
        Set<TLAExpression> oldAssignedVariables = new HashSet<>(assignedVariables);

        for (PlusCalStatement statement : plusCalIf.getYes()) {
            statement.accept(this);
        }

        this.previousStatement = oldPreviousStatement;
        Set<TLAExpression> assignedYes = new HashSet<>(this.assignedVariables);

        // restore variable assignment before visiting `else` set of
        // statements
        this.assignedVariables = oldAssignedVariables;
        for (PlusCalStatement statement : plusCalIf.getNo()) {
            statement.accept(this);
        }

        // the set of variables assigned should be the union of the variables
        // assigned in the `then` and `else` branches of the conditional.
        this.assignedVariables.addAll(assignedYes);
        this.previousStatement = plusCalIf;
        return null;
    }

    public Void visit(PlusCalEither plusCalEither) {
        checkProcedureCall(plusCalEither);
        checkReturnOrGoto(plusCalEither);
        checkIfEither(plusCalEither);

        // save the actual statement preceding this 'if' statement so that
        // both all cases of this either branch have a consistent view of
        // the previous statement.
        PlusCalStatement oldPreviousStatement = previousStatement;

        // keep a copy of the variables assigned before this `either` statement.
        final Set<TLAExpression> oldAssignedVariables = new HashSet<>(assignedVariables);
        List<Set<TLAExpression>> assignmentCases = new ArrayList<>();

        for (List<PlusCalStatement> cases : plusCalEither.getCases()) {
            // restore the variables assigned before validating each of the cases
            // of an `either` statement. Assigning to the same variable once
            // in different cases of `either` is not an error.
            this.assignedVariables = new HashSet<>(oldAssignedVariables);

            for (PlusCalStatement statement : cases) {
                this.previousStatement = oldPreviousStatement;
                statement.accept(this);
            }

            assignmentCases.add(new HashSet<>(this.assignedVariables));
        }

        // the set of all assigned variables after an `either` statement should
        // be the union of all variables assigned in all cases.
        for (Set<TLAExpression> assignments : assignmentCases) {
            this.assignedVariables.addAll(assignments);
        }

        this.previousStatement = plusCalEither;
        return null;
    }

    public Void visit(PlusCalAssignment plusCalAssignment) {
        checkProcedureCall(plusCalAssignment);
        checkReturnOrGoto(plusCalAssignment);
        checkIfEither(plusCalAssignment);
        this.previousStatement = plusCalAssignment;

        for (PlusCalAssignmentPair pair : plusCalAssignment.getPairs()) {
            if (assignedVariables.contains(pair.getLhs())) {
                missingLabel(plusCalAssignment);
            } else {
                assignedVariables.add(pair.getLhs());
            }
        }

        return null;
    }

    public Void visit(PlusCalReturn plusCalReturn) {
        // `return` statements can follow procedure calls -- no checks here
        checkReturnOrGoto(plusCalReturn);
        checkIfEither(plusCalReturn);

        this.previousStatement = plusCalReturn;
        return null;
    }

    public Void visit(PlusCalSkip plusCalSkip) {
        checkProcedureCall(plusCalSkip);
        checkReturnOrGoto(plusCalSkip);
        checkIfEither(plusCalSkip);

        this.previousStatement = plusCalSkip;

        return null;
    }

    public Void visit(PlusCalCall plusCalCall) {
        checkProcedureCall(plusCalCall);
        checkReturnOrGoto(plusCalCall);
        checkIfEither(plusCalCall);
        this.previousStatement = plusCalCall;

        return null;
    }

    public Void visit(PlusCalMacroCall macroCall) {
        checkProcedureCall(macroCall);
        checkReturnOrGoto(macroCall);
        checkIfEither(macroCall);
        this.previousStatement = macroCall;
        return null;
    }

    public Void visit(PlusCalWith plusCalWith) {
        checkProcedureCall(plusCalWith);
        checkReturnOrGoto(plusCalWith);
        checkIfEither(plusCalWith);
        this.previousStatement = plusCalWith;

        // make sure all statements in the body of a 'with' expression
        // do not have labels in them
        boolean oldLabelsAllowed = labelsAllowed;
        labelsAllowed = false;
        for (PlusCalStatement statement : plusCalWith.getBody()) {
            statement.accept(this);
        }

        labelsAllowed = oldLabelsAllowed;
        return null;
    }

    public Void visit(PlusCalPrint plusCalPrint) {
        checkProcedureCall(plusCalPrint);
        checkReturnOrGoto(plusCalPrint);
        checkIfEither(plusCalPrint);
        this.previousStatement = plusCalPrint;

        return null;
    }

    public Void visit(PlusCalAssert plusCalAssert) {
        checkProcedureCall(plusCalAssert);
        checkReturnOrGoto(plusCalAssert);
        checkIfEither(plusCalAssert);
        this.previousStatement = plusCalAssert;

        return null;
    }

    public Void visit(PlusCalAwait plusCalAwait) {
        checkProcedureCall(plusCalAwait);
        checkReturnOrGoto(plusCalAwait);
        checkIfEither(plusCalAwait);
        this.previousStatement = plusCalAwait;

        return null;
    }

    public Void visit(PlusCalGoto plusCalGoto) {
        // `goto` statements can follow procedure calls -- no checks here
        checkReturnOrGoto(plusCalGoto);
        checkIfEither(plusCalGoto);
        this.previousStatement = plusCalGoto;

        return null;
    }

    public Void visit(ModularPlusCalYield modularPlusCalYield) {
        throw new Unreachable();
    }

    private void missingLabel(PlusCalStatement statement) {
        this.ctx.error(new MissingLabelIssue(statement));
    }

    private void labelNotAllowed(PlusCalStatement statement) {
        this.ctx.error(new LabelNotAllowedIssue(statement));
    }

    private void reservedLabelName(PlusCalStatement statement) {
        this.ctx.error(new ReservedLabelNameIssue(statement));
    }

    // checks whether the statement given is the first of an archetype/procedure/process,
    // or if it is a labeled statement. The label checks in this visitor do not flag
    // the case when the first statement is not labeled since that is already taken care
    // of by ModularPlusCalValidationVisitor.
    private boolean firstOrLabeled(PlusCalStatement statement) {
        return (statement == null) || (statement instanceof PlusCalLabeledStatements);
    }

    // PlusCal mandates that statements preceded by a procedure call need to be labeled,
    // unless they are a `return` or `goto` statement.
    private void checkProcedureCall(PlusCalStatement statement) {
        if (previousStatement instanceof PlusCalCall) {
            missingLabel(statement);
        }
    }

    // every statement followed by `return` or `goto` need to be labeled
    private void checkReturnOrGoto(PlusCalStatement statement) {
        if ((previousStatement instanceof PlusCalReturn) || (previousStatement instanceof PlusCalGoto)) {
            missingLabel(statement);
        }
    }

    // a statement must be labeled if it is preceded by an `if` or `either` statement
    // that includes a labeled statement, `goto`, `call` or `return` anywhere within it.
    private void checkIfEither(PlusCalStatement statement) {
        if ((previousStatement instanceof PlusCalIf) || (previousStatement instanceof PlusCalEither)) {
            boolean needsLabel = previousStatement.accept(new IfEitherNextStatementRequiresLabel());

            if (needsLabel) {
                missingLabel(statement);
            }
        }
    }

    private boolean isReserved(PlusCalLabel label) {
        return IntStream.range(0, RESERVED_LABELS.length).
                anyMatch(i -> RESERVED_LABELS[i].equals(label.getName()));
    }
}
