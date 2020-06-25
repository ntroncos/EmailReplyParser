package com.edlio.emailreplyparser;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Email {
	private List<Fragment> fragments;

	public Email(List<Fragment> fragments) {
		this.fragments = new LinkedList<>(fragments);
	}

	public List<Fragment> getFragments() {
		return fragments;
	}

	public String getVisibleText() {
		return fragments.stream()
				.filter(fragment -> !fragment.isHidden())
				.map(Fragment::getContent)
				.collect(Collectors.joining("\n"))
				.stripTrailing();
	}

	public String getHiddenText() {
		return fragments.stream()
				.filter(Fragment::isHidden)
				.map(Fragment::getContent)
				.collect(Collectors.joining("\n"))
				.stripTrailing();
	}

}
