package org.eclipse.twig.core.documentModel.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.Segment;

import org.eclipse.core.resources.IProject;
import org.eclipse.php.internal.core.documentModel.parser.Scanner;
import org.eclipse.php.internal.core.documentModel.parser.StateStack;
import org.eclipse.php.internal.core.documentModel.parser.regions.PHPRegionTypes;
import org.eclipse.php.internal.core.documentModel.partitioner.PHPPartitionTypes;
import org.eclipse.php.internal.core.preferences.TaskPatternsProvider;
import org.eclipse.php.internal.core.util.collections.IntHashtable;
import org.eclipse.twig.core.documentModel.parser.regions.TwigRegionTypes;
import org.eclipse.twig.core.util.Debug;
import org.eclipse.wst.sse.core.internal.parser.ContextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.utils.StringUtils;


/**
 *
 * Next Steps: check how this works - should be generated
 * by jflex.
 * 
 * 
 * see {@link AbstractTwigLexer} 34
 * php.core : /Resources/parserTools/parser/php53 ast_scanner.flex
 *
 *
 * @author "Robert Gruendler <r.gruendler@gmail.com>"
 *
 */
@SuppressWarnings("restriction")
public abstract class AbstractTwigLexer implements Scanner, TwigRegionTypes {

	// should be auto-generated by jflex
	protected abstract int getZZEndRead();

	protected abstract int getZZLexicalState();

	protected abstract int getZZMarkedPos();

	protected abstract int getZZPushBackPosition();

	protected abstract int getZZStartRead();

	protected abstract void pushBack(int i);

	public abstract char[] getZZBuffer();

	public abstract void yybegin(int newState);

	public abstract int yylength();

	public abstract String yytext();

	public abstract void reset(Reader reader, char[] buffer, int[] parameters);

	public abstract int yystate();

	public abstract int[] getParamenters();

	/**
	 * This character denotes the end of file
	 */
	final public static int YYEOF = -1;

	protected static final boolean isLowerCase(final String text) {
		if (text == null)
			return false;
		for (int i = 0; i < text.length(); i++)
			if (!Character.isLowerCase(text.charAt(i)))
				return false;
		return true;
	}

	protected boolean asp_tags = true;

	protected int defaultReturnValue = -1;

	protected int firstPos = -1; // the first position in the array

	protected String heredoc = null;
	protected String nowdoc = null;
	protected int heredoc_len = 0;
	protected int nowdoc_len = 0;
	protected StateStack twigStack;

	/**
	 * build a key that represents the current state of the lexer.
	 */
	private int buildStateKey() {
		int rv = getZZLexicalState();

		for (int i = 0; i < twigStack.size(); i++)
			rv = 31 * rv + twigStack.get(i);
		for (int i = 0; i < heredoc_len; i++)
			rv = 31 * rv + heredoc.charAt(i);
		for (int i = 0; i < nowdoc_len; i++)
			rv = 31 * rv + nowdoc.charAt(i);
		return rv;
	}

	public Object createLexicalStateMemento() {
		// buffered token state
		if (bufferedTokens != null && !bufferedTokens.isEmpty()) {
			return bufferedState;
		}

		// System.out.println("lexerStates size:" + lexerStates.size());
		final int key = buildStateKey();
		Object state = getLexerStates().get(key);
		if (state == null) {
			state = new BasicLexerState(this);
			getLexerStates().put(key, state);
		}
		return state;
	}

	// A pool of states. To avoid creation of a new state on each createMemento.
	protected abstract IntHashtable getLexerStates();

	public boolean getAspTags() {
		return asp_tags;
	}

	// lex to the EOF. and return the ending state.
	public Object getEndingState() throws IOException {
		lexToEnd();
		return createLexicalStateMemento();
	}

	/**
	 * return the index where start we started to lex.
	 */
	public int getFirstIndex() {
		return firstPos;
	}

	public int getMarkedPos() {
		return getZZMarkedPos();
	}

	public void getText(final int start, final int length, final Segment s) {
		if (start + length > getZZEndRead())
			throw new RuntimeException("bad segment !!"); //$NON-NLS-1$
		s.array = getZZBuffer();
		s.offset = start;
		s.count = length;
	}

	public int getTokenStart() {
		return getZZStartRead() - getZZPushBackPosition();
	}

	/**
	 * reset to a new segment. this do not change the state of the lexer. This
	 * method is used to scan nore than one segment as if the are one segment.
	 */
	public void reset(final Segment s) {
		reset(s.array, s.offset, s.count);
	}

	public void initialize(final int state) {
		twigStack = new StateStack();		
		yybegin(state);
	}

	/**
	 * reset to a new segment. this do not change the state of the lexer. This
	 * method is used to scan nore than one segment as if the are one segment.
	 */

	// lex to the end of the stream.
	public String lexToEnd() throws IOException {
		String curr = yylex();
		String last = curr;
		while (curr != null) {
			last = curr;
			curr = yylex();
		}
		return last;
	}

	public String lexToTokenAt(final int offset) throws IOException {
		if (firstPos + offset < getZZMarkedPos())
			throw new RuntimeException("Bad offset"); //$NON-NLS-1$
		String t = yylex();
		while (getZZMarkedPos() < firstPos + offset && t != null)
			t = yylex();
		return t;
	}

	protected void popState() {
		yybegin(twigStack.popStack());
	}

	protected void pushState(final int state) {
		twigStack.pushStack(getZZLexicalState());
		yybegin(state);
	}

	public void setAspTags(final boolean b) {
		asp_tags = b;
	}

	public void setState(final Object state) {
		((LexerState) state).restoreState(this);
	}

	public int yystart() {
		return getZZStartRead();
	}

	public LinkedList<ITextRegion> bufferedTokens = null;
	public int bufferedLength;
	public Object bufferedState;

	/**
	 * @return the next token from the php lexer
	 * @throws IOException
	 */
	public String getNextToken() throws IOException {
		if (bufferedTokens != null) {
			if (bufferedTokens.isEmpty()) {
				bufferedTokens = null;
			} else {
				return removeFromBuffer();
			}
		}

		bufferedState = createLexicalStateMemento();
		String yylex = yylex();
		if (PHPPartitionTypes.isPHPDocCommentState(yylex)) {
			final StringBuffer buffer = new StringBuffer();
			int length = 0;
			while (PHPPartitionTypes.isPHPDocCommentState(yylex)) {
				buffer.append(yytext());
				yylex = yylex();
				length++;
			}
			bufferedTokens = new LinkedList<ITextRegion>();
			checkForTodo(bufferedTokens, PHPRegionTypes.PHPDOC_COMMENT, 0,
					length, buffer.toString());
			bufferedTokens.add(new ContextRegion(yylex, 0, yylength(),
					yylength()));
			yylex = removeFromBuffer();
		} else if (PHPPartitionTypes.isPHPCommentState(yylex)) {
			bufferedTokens = new LinkedList<ITextRegion>();
			checkForTodo(bufferedTokens, yylex, 0, yylength(), yytext());
			yylex = removeFromBuffer();
		}

		if (yylex == TWIG_CLOSE || yylex == TWIG_STMT_CLOSE) {
			pushBack(getLength());
		}

		return yylex;
	}

	/**
	 * @return the last token from buffer
	 */
	private String removeFromBuffer() {
		ITextRegion region = (ITextRegion) bufferedTokens.removeFirst();
		bufferedLength = region.getLength();
		return region.getType();
	}

	public int getLength() {
		return bufferedTokens == null ? yylength() : bufferedLength;
	}

	private Pattern[] todos;

	public void setPatterns(IProject project) {
		if (project != null) {
			todos = TaskPatternsProvider.getInstance().getPatternsForProject(
					project);
		} else {
			todos = TaskPatternsProvider.getInstance()
					.getPetternsForWorkspace();
		}
	}

	/**
	 * @param bufferedTokens2
	 * @param token
	 * @param commentStart
	 * @param commentLength
	 * @param comment
	 * @return a list of todo ITextRegion
	 */
	private void checkForTodo(List<ITextRegion> result, String token,
			int commentStart, int commentLength, String comment) {
		ArrayList<Matcher> matchers = createMatcherList(comment);
		int startPosition = 0;

		Matcher matcher = getMinimalMatcher(matchers, startPosition);
		ITextRegion tRegion = null;
		while (matcher != null) {
			int startIndex = matcher.start();
			int endIndex = matcher.end();
			if (startIndex != startPosition) {
				tRegion = new ContextRegion(token,
						commentStart + startPosition, startIndex
								- startPosition, startIndex - startPosition);
				result.add(tRegion);
			}
			tRegion = new ContextRegion(PHPRegionTypes.PHPDOC_TODO,
					commentStart + startIndex, endIndex - startIndex, endIndex
							- startIndex);
			result.add(tRegion);
			startPosition = endIndex;
			matcher = getMinimalMatcher(matchers, startPosition);
		}
		final int length = commentLength - startPosition;
		if (length != 0) {
			result.add(new ContextRegion(token, commentStart + startPosition,
					length, length));
		}

		// String[] words = comment.split("\\W+");
		// int startPosition = 0;
		// for (int i = 0; i < words.length; i++) {
		// String word = words[i];
		// ArrayList<Matcher> matchers = createMatcherList(word);
		//
		// Matcher matcher = getMinimalMatcher(matchers, 0);
		// ITextRegion tRegion = null;
		// int index = comment.indexOf(word, startPosition);
		// if (matcher != null) {
		// int startIndex = matcher.start();
		// int endIndex = matcher.end();
		// if (endIndex - startIndex == word.length()) {
		//
		// if (index - startPosition > 0) {
		// tRegion = new ContextRegion(token, commentStart
		// + startPosition, index - startPosition, index
		// - startPosition);
		// result.add(tRegion);
		// startPosition = index;
		// }
		// tRegion = new ContextRegion(PHPRegionTypes.PHPDOC_TODO,
		// commentStart + index, endIndex - startIndex,
		// endIndex - startIndex);
		// result.add(tRegion);
		// startPosition += endIndex;
		// } else {
		// final int length = word.length() - startPosition;
		// result.add(new ContextRegion(token, commentStart
		// + startPosition, length, length));
		// }
		// } else {
		// final int length = word.length() + index - startPosition;
		// result.add(new ContextRegion(token, commentStart
		// + startPosition, length, length));
		// startPosition += length;
		// }
		// }
		// if (words.length == 0) {
		// result.add(new ContextRegion(token, commentStart, commentLength,
		// commentLength));
		// }
	}

	private ArrayList<Matcher> createMatcherList(String content) {
		ArrayList<Matcher> list = new ArrayList<Matcher>(todos.length);
		for (int i = 0; i < todos.length; i++) {
			list.add(i, todos[i].matcher(content));
		}
		return list;
	}

	private Matcher getMinimalMatcher(ArrayList<Matcher> matchers,
			int startPosition) {
		Matcher minimal = null;
		int size = matchers.size();
		for (int i = 0; i < size;) {
			Matcher tmp = (Matcher) matchers.get(i);
			if (tmp.find(startPosition)) {
				if (minimal == null || tmp.start() < minimal.start()) {
					minimal = tmp;
				}
				i++;
			} else {
				matchers.remove(i);
				size--;
			}
		}
		return minimal;
	}

	private static class BasicLexerState implements LexerState {

		private final byte lexicalState;
		private StateStack phpStack;

		public BasicLexerState(AbstractTwigLexer lexer) {
			if (!lexer.twigStack.isEmpty()) {
				phpStack = lexer.twigStack.createClone();
			}
			lexicalState = (byte) lexer.getZZLexicalState();
		}

		@Override
		public boolean equals(final Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (!(o instanceof BasicLexerState))
				return false;
			final BasicLexerState tmp = (BasicLexerState) o;
			if (tmp.lexicalState != lexicalState)
				return false;
			if (phpStack != null && !phpStack.equals(tmp.phpStack))
				return false;
			return phpStack == tmp.phpStack;
		}

		public boolean equalsCurrentStack(final LexerState obj) {
			if (obj == this)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof BasicLexerState))
				return false;
			final BasicLexerState tmp = (BasicLexerState) obj;
			if (tmp.lexicalState != lexicalState)
				return false;
			final StateStack activeStack = getActiveStack();
			final StateStack otherActiveStack = tmp.getActiveStack();
			if (!(activeStack == otherActiveStack || activeStack != null
					&& activeStack.equals(otherActiveStack)))
				return false;
			return true;
		}

		public boolean equalsTop(final LexerState obj) {
			return obj != null && obj.getTopState() == lexicalState;
		}

		protected StateStack getActiveStack() {
			return phpStack;
		}

		public int getTopState() {
			return lexicalState;
		}

		public boolean isSubstateOf(final int state) {
			if (lexicalState == state)
				return true;
			final StateStack activeStack = getActiveStack();
			if (activeStack == null)
				return false;
			return activeStack.contains(state);
		}

		public void restoreState(final Scanner scanner) {
			final AbstractTwigLexer lexer = (AbstractTwigLexer) scanner;

			if (phpStack == null)
				lexer.twigStack.clear();
			else
				lexer.twigStack.copyFrom(phpStack);

			lexer.yybegin(lexicalState);
		}

		@Override
		public String toString() {
			final StateStack stack = getActiveStack();
			final String stackStr = stack == null ? "null" : stack.toString(); //$NON-NLS-1$
			return "Stack: " + stackStr + ", currState: " + lexicalState; //$NON-NLS-1$ //$NON-NLS-2$
		}

	}

	private static class HeredocState implements LexerState {
		private final String myHeredoc;
		private final String myNowdoc;
		private final BasicLexerState theState;

		public HeredocState(final BasicLexerState state, AbstractTwigLexer lexer) {
			theState = state;
			myHeredoc = lexer.heredoc;
			myNowdoc = lexer.nowdoc;
		}

		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((myHeredoc == null) ? 0 : myHeredoc.hashCode());
			result = prime * result
					+ ((myNowdoc == null) ? 0 : myNowdoc.hashCode());
			result = prime * result
					+ ((theState == null) ? 0 : theState.hashCode());
			return result;
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			HeredocState other = (HeredocState) obj;
			if (myHeredoc == null) {
				if (other.myHeredoc != null)
					return false;
			} else if (!myHeredoc.equals(other.myHeredoc))
				return false;
			if (myNowdoc == null) {
				if (other.myNowdoc != null)
					return false;
			} else if (!myNowdoc.equals(other.myNowdoc))
				return false;
			if (theState == null) {
				if (other.theState != null)
					return false;
			} else if (!theState.equals(other.theState))
				return false;
			return true;
		}

		public boolean equalsCurrentStack(final LexerState obj) {
			if (obj == this)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof HeredocState))
				return false;
			return theState.equals(((HeredocState) obj).theState);
		}

		public boolean equalsTop(final LexerState obj) {
			return theState.equalsTop(obj);
		}

		public int getTopState() {
			return theState.getTopState();
		}

		public boolean isSubstateOf(final int state) {
			return theState.isSubstateOf(state);
		}

		public void restoreState(final Scanner scanner) {
			final AbstractTwigLexer lexer = (AbstractTwigLexer) scanner;
			theState.restoreState(lexer);

			if (myHeredoc != null) {
				lexer.heredoc = myHeredoc;
				lexer.heredoc_len = myHeredoc.length();
			}
			if (myNowdoc != null) {
				lexer.nowdoc = myNowdoc;
				lexer.nowdoc_len = myNowdoc.length();
			}
		}
	}
	
	

}
