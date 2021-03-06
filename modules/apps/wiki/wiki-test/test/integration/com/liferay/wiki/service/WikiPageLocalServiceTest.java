/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.wiki.service;

import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.test.AssertUtils;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.rule.Sync;
import com.liferay.portal.kernel.test.rule.SynchronousDestinationTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.model.Group;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.MainServletTestRule;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.asset.model.AssetEntry;
import com.liferay.portlet.asset.model.AssetLink;
import com.liferay.portlet.asset.service.AssetCategoryLocalServiceUtil;
import com.liferay.portlet.asset.service.AssetEntryLocalServiceUtil;
import com.liferay.portlet.asset.service.AssetLinkLocalServiceUtil;
import com.liferay.portlet.asset.service.AssetTagLocalServiceUtil;
import com.liferay.portlet.expando.model.ExpandoBridge;
import com.liferay.portlet.expando.model.ExpandoColumn;
import com.liferay.portlet.expando.model.ExpandoColumnConstants;
import com.liferay.portlet.expando.model.ExpandoValue;
import com.liferay.portlet.expando.util.test.ExpandoTestUtil;
import com.liferay.wiki.exception.DuplicatePageException;
import com.liferay.wiki.exception.NoSuchPageResourceException;
import com.liferay.wiki.exception.NodeChangeException;
import com.liferay.wiki.model.WikiNode;
import com.liferay.wiki.model.WikiPage;
import com.liferay.wiki.util.WikiTestUtil;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Manuel de la Peña
 * @author Roberto Díaz
 */
@Sync
public class WikiPageLocalServiceTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(), MainServletTestRule.INSTANCE,
			SynchronousDestinationTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_node = WikiTestUtil.addNode(_group.getGroupId());
	}

	@Test
	public void testChangeChildPageNode() throws Exception {
		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"ParentPage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _node.getNodeId(), "ChildPage",
			RandomTestUtil.randomString(), "ParentPage", true, serviceContext);

		WikiPageLocalServiceUtil.changeNode(
			TestPropsValues.getUserId(), _node.getNodeId(), "ChildPage",
			destinationNode.getNodeId(), serviceContext);

		WikiPage parentPage = WikiPageLocalServiceUtil.getPage(
			_node.getNodeId(), "ParentPage");
		WikiPage childPage = WikiPageLocalServiceUtil.getPage(
			destinationNode.getNodeId(), "ChildPage");

		Assert.assertEquals(_node.getNodeId(), parentPage.getNodeId());
		Assert.assertEquals(destinationNode.getNodeId(), childPage.getNodeId());
		Assert.assertTrue(Validator.isNull(childPage.getParentTitle()));
	}

	@Test
	public void testChangePageNode() throws Exception {
		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.changeNode(
			TestPropsValues.getUserId(), _node.getNodeId(), page.getTitle(),
			destinationNode.getNodeId(), serviceContext);

		WikiPageLocalServiceUtil.getPage(
			destinationNode.getNodeId(), page.getTitle());
	}

	@Test
	public void testChangePageNodeWithChildHierarchy() throws Exception {
		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"ParentPage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		List<WikiPage> childPages = createPageHierarchy(
			"ParentPage", 10, serviceContext);

		WikiPageLocalServiceUtil.changeNode(
			TestPropsValues.getUserId(), _node.getNodeId(), "ParentPage",
			destinationNode.getNodeId(), serviceContext);

		WikiPage page = WikiPageLocalServiceUtil.getPage(
			destinationNode.getNodeId(), "ParentPage");

		Assert.assertEquals(destinationNode.getNodeId(), page.getNodeId());

		for (WikiPage childPage : childPages) {
			childPage = WikiPageLocalServiceUtil.getPage(
				childPage.getResourcePrimKey());

			Assert.assertEquals(
				destinationNode.getNodeId(), childPage.getNodeId());
		}
	}

	@Test
	public void testChangePageNodeWithChildPageNameDuplication()
		throws Exception {

		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"ParentPage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _node.getNodeId(),
			"DuplicatedTitlePage", RandomTestUtil.randomString(), "ParentPage",
			true, serviceContext);

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(),
			destinationNode.getNodeId(), "DuplicatedTitlePage", true);

		try {
			WikiPageLocalServiceUtil.changeNode(
				TestPropsValues.getUserId(), _node.getNodeId(), "ParentPage",
				destinationNode.getNodeId(), serviceContext);

			Assert.fail();
		}
		catch (NodeChangeException nce) {
			Assert.assertEquals("DuplicatedTitlePage", nce.getPageTitle());
			Assert.assertEquals(destinationNode.getName(), nce.getNodeName());
			Assert.assertEquals(
				NodeChangeException.DUPLICATE_PAGE, nce.getType());
		}
	}

	@Test
	public void testChangePageNodeWithPageNameDuplication() throws Exception {
		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiPage page = WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"DuplicatedTitlePage", true);

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(),
			destinationNode.getNodeId(), "DuplicatedTitlePage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		try {
			WikiPageLocalServiceUtil.changeNode(
				page.getUserId(), _node.getNodeId(), "DuplicatedTitlePage",
				destinationNode.getNodeId(), serviceContext);

			Assert.fail();
		}
		catch (NodeChangeException nce) {
			Assert.assertEquals("DuplicatedTitlePage", nce.getPageTitle());
			Assert.assertEquals(destinationNode.getName(), nce.getNodeName());
			Assert.assertEquals(
				NodeChangeException.DUPLICATE_PAGE, nce.getType());
		}
	}

	@Test
	public void testChangePageNodeWithRedirectPage() throws Exception {
		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"InitialTitlePage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(), "InitialTitlePage",
			"RenamedPage", serviceContext);

		WikiPageLocalServiceUtil.changeNode(
			TestPropsValues.getUserId(), _node.getNodeId(), "RenamedPage",
			destinationNode.getNodeId(), serviceContext);

		WikiPage page = WikiPageLocalServiceUtil.getPage(
			destinationNode.getNodeId(), "RenamedPage");
		WikiPage redirectPage = WikiPageLocalServiceUtil.getPage(
			destinationNode.getNodeId(), "InitialTitlePage");

		Assert.assertEquals(destinationNode.getNodeId(), page.getNodeId());
		Assert.assertEquals(
			destinationNode.getNodeId(), redirectPage.getNodeId());
		Assert.assertEquals("RenamedPage", redirectPage.getRedirectTitle());
	}

	@Test
	public void testChangePageNodeWithRedirectPageNameDuplication()
		throws Exception {

		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"DuplicatedTitlePage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(),
			"DuplicatedTitlePage", "RenamedPage",  serviceContext);

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(),
			destinationNode.getNodeId(), "DuplicatedTitlePage", true);

		try {
			WikiPageLocalServiceUtil.changeNode(
				TestPropsValues.getUserId(), _node.getNodeId(), "RenamedPage",
				destinationNode.getNodeId(), serviceContext);

			Assert.fail();
		}
		catch (NodeChangeException nce) {
			Assert.assertEquals("DuplicatedTitlePage", nce.getPageTitle());
			Assert.assertEquals(destinationNode.getName(), nce.getNodeName());
			Assert.assertEquals(
				NodeChangeException.DUPLICATE_PAGE, nce.getType());
		}
	}

	@Test
	public void testChangeParent() throws Exception {
		testChangeParent(false);
	}

	@Test
	public void testChangeParentWithExpando() throws Exception {
		testChangeParent(true);
	}

	@Test
	public void testChangeRedirectPageNode() throws Exception {
		WikiNode destinationNode = WikiTestUtil.addNode(_group.getGroupId());

		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"InitialTitlePage", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(), "InitialTitlePage",
			"RenamedPage",  serviceContext);

		try {
			WikiPageLocalServiceUtil.changeNode(
				TestPropsValues.getUserId(), _node.getNodeId(),
				"InitialTitlePage", destinationNode.getNodeId(),
				serviceContext);

			Assert.fail();
		}
		catch (NodeChangeException nce) {
			Assert.assertEquals("InitialTitlePage", nce.getPageTitle());
			Assert.assertEquals(_node.getName(), nce.getNodeName());
			Assert.assertEquals(
				NodeChangeException.REDIRECT_PAGE, nce.getType());
		}
	}

	@Test
	public void testCopyPage() throws Exception {
		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		WikiTestUtil.addWikiAttachment(
			page.getUserId(), page.getNodeId(), page.getTitle(), getClass());

		List<FileEntry> attachmentsFileEntries =
			page.getAttachmentsFileEntries();

		WikiPage copyPage = WikiTestUtil.copyPage(
			page, true,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		List<FileEntry> copyAttachmentsFileEntries =
			copyPage.getAttachmentsFileEntries();

		Assert.assertEquals(
			copyAttachmentsFileEntries.size(), attachmentsFileEntries.size());

		FileEntry fileEntry = attachmentsFileEntries.get(0);
		FileEntry copyFileEntry = copyAttachmentsFileEntries.get(0);

		Assert.assertEquals(
			copyFileEntry.getExtension(), fileEntry.getExtension());
		Assert.assertEquals(
			copyFileEntry.getMimeType(), fileEntry.getMimeType());
		Assert.assertEquals(copyFileEntry.getTitle(), fileEntry.getTitle());
		Assert.assertEquals(copyFileEntry.getSize(), fileEntry.getSize());
	}

	@Test(expected = NoSuchPageResourceException.class)
	public void testDeletePage() throws Exception {
		WikiPage page = WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"TestPage", true);

		WikiPageLocalServiceUtil.deletePage(page);

		WikiPageLocalServiceUtil.getPage(page.getResourcePrimKey());
	}

	@Test
	public void testDeleteTrashedPageWithExplicitTrashedRedirectPage()
		throws Exception {

		WikiPage[] pages = WikiTestUtil.addRenamedTrashedPage(
			_group.getGroupId(), _node.getNodeId(), true);

		WikiPage page = pages[0];
		WikiPage redirectPage = pages[1];

		WikiPageLocalServiceUtil.deletePage(page);

		try {
			WikiPageLocalServiceUtil.getPage(page.getResourcePrimKey());

			Assert.fail();
		}
		catch (NoSuchPageResourceException nspre) {
			redirectPage = WikiPageLocalServiceUtil.getPage(
				redirectPage.getResourcePrimKey());

			Assert.assertNull(redirectPage.fetchRedirectPage());
		}
	}

	@Test(expected = NoSuchPageResourceException.class)
	public void testDeleteTrashedPageWithImplicitTrashedRedirectPage()
		throws Exception {

		WikiPage[] pages = WikiTestUtil.addRenamedTrashedPage(
			_group.getGroupId(), _node.getNodeId(), false);

		WikiPage page = pages[0];
		WikiPage redirectPage = pages[1];

		WikiPageLocalServiceUtil.deletePage(page);

		try {
			WikiPageLocalServiceUtil.getPage(page.getResourcePrimKey());

			Assert.fail();
		}
		catch (NoSuchPageResourceException nsrpe) {
			WikiPageLocalServiceUtil.getPage(redirectPage.getResourcePrimKey());
		}
	}

	@Test
	public void testDeleteTrashedPageWithRestoredChildPage() throws Exception {
		WikiPage[] pages = WikiTestUtil.addTrashedPageWithChildPage(
			_group.getGroupId(), _node.getNodeId(), true);

		WikiPage parentPage = pages[0];
		WikiPage childPage = pages[1];

		WikiPageLocalServiceUtil.restorePageFromTrash(
			TestPropsValues.getUserId(), childPage);

		WikiPageLocalServiceUtil.deletePage(parentPage);

		try {
			WikiPageLocalServiceUtil.getPage(parentPage.getResourcePrimKey());

			Assert.fail();
		}
		catch (NoSuchPageResourceException nspre) {
			childPage = WikiPageLocalServiceUtil.getPage(
				childPage.getResourcePrimKey());

			Assert.assertNull(childPage.fetchParentPage());
			Assert.assertEquals(
				WorkflowConstants.STATUS_APPROVED, childPage.getStatus());
		}
	}

	@Test
	public void testDeleteTrashedPageWithRestoredRedirectPage()
		throws Exception {

		WikiPage[] pages = WikiTestUtil.addRenamedTrashedPage(
			_group.getGroupId(), _node.getNodeId(), true);

		WikiPage page = pages[0];
		WikiPage redirectPage = pages[1];

		WikiPageLocalServiceUtil.restorePageFromTrash(
			TestPropsValues.getUserId(), redirectPage);

		WikiPageLocalServiceUtil.deletePage(page);

		try {
			WikiPageLocalServiceUtil.getPage(page.getResourcePrimKey());

			Assert.fail();
		}
		catch (NoSuchPageResourceException nspre) {
			redirectPage = WikiPageLocalServiceUtil.getPageByPageId(
				redirectPage.getPageId());

			Assert.assertNull(redirectPage.fetchRedirectPage());
			Assert.assertEquals(
				WorkflowConstants.STATUS_APPROVED, redirectPage.getStatus());
		}
	}

	@Test
	public void testDeleteTrashedParentPageWithExplicitTrashedChildPage()
		throws Exception {

		WikiPage[] pages = WikiTestUtil.addTrashedPageWithChildPage(
			_group.getGroupId(), _node.getNodeId(), true);

		WikiPage parentPage = pages[0];
		WikiPage childPage = pages[1];

		WikiPageLocalServiceUtil.deletePage(parentPage);

		try {
			WikiPageLocalServiceUtil.getPage(parentPage.getResourcePrimKey());

			Assert.fail();
		}
		catch (NoSuchPageResourceException nspre) {
			childPage = WikiPageLocalServiceUtil.getPageByPageId(
				childPage.getPageId());

			Assert.assertNull(childPage.fetchParentPage());
		}
	}

	@Test(expected = NoSuchPageResourceException.class)
	public void testDeleteTrashedParentPageWithImplicitTrashedChildPage()
		throws Exception {

		WikiPage[] pages = WikiTestUtil.addTrashedPageWithChildPage(
			_group.getGroupId(), _node.getNodeId(), false);

		WikiPage parentPage = pages[0];
		WikiPage childPage = pages[1];

		WikiPageLocalServiceUtil.deletePage(parentPage);

		try {
			WikiPageLocalServiceUtil.getPage(parentPage.getResourcePrimKey());

			Assert.fail();
		}
		catch (NoSuchPageResourceException nspre) {
			WikiPageLocalServiceUtil.getPage(childPage.getResourcePrimKey());
		}
	}

	@Test
	public void testGetNoAssetPages() throws Exception {
		WikiTestUtil.addPage(_group.getGroupId(), _node.getNodeId(), true);

		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		AssetEntry assetEntry = AssetEntryLocalServiceUtil.fetchEntry(
			WikiPage.class.getName(), page.getResourcePrimKey());

		Assert.assertNotNull(assetEntry);

		AssetEntryLocalServiceUtil.deleteAssetEntry(assetEntry);

		List<WikiPage> pages = WikiPageLocalServiceUtil.getNoAssetPages();

		Assert.assertEquals(1, pages.size());
		Assert.assertEquals(page, pages.get(0));
	}

	@Test
	public void testGetPage() throws Exception {
		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		WikiPage retrievedPage = WikiPageLocalServiceUtil.getPage(
			page.getResourcePrimKey());

		Assert.assertEquals(retrievedPage.getPageId(), page.getPageId());
	}

	@Test
	public void testRenamePage() throws Exception {
		testRenamePage(false);
	}

	@Test(expected = DuplicatePageException.class)
	public void testRenamePageSameName() throws Exception {
		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(), page.getTitle(),
			page.getTitle(), true, serviceContext);
	}

	@Test
	public void testRenamePageWithExpando() throws Exception {
		testRenamePage(true);
	}

	@Test
	public void testRenameRenamedPage() throws Exception {
		WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _group.getGroupId(), _node.getNodeId(),
			"A", true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(), "A", "B", true,
			serviceContext);

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(), "A", "C", true,
			serviceContext);

		WikiPage pageA = WikiPageLocalServiceUtil.getPage(
			_node.getNodeId(), "A");
		WikiPage pageB = WikiPageLocalServiceUtil.getPage(
			_node.getNodeId(), "B");
		WikiPage pageC = WikiPageLocalServiceUtil.getPage(
			_node.getNodeId(), "C");

		Assert.assertEquals("C", pageA.getRedirectTitle());
		Assert.assertEquals(StringPool.BLANK, pageB.getRedirectTitle());
		Assert.assertEquals(StringPool.BLANK, pageC.getRedirectTitle());
		Assert.assertEquals("Renamed as C", pageA.getSummary());
		Assert.assertEquals("Summary", pageB.getSummary());
		Assert.assertEquals(StringPool.BLANK, pageC.getSummary());
		Assert.assertEquals("[[C]]", pageA.getContent());
		Assert.assertEquals("[[B]]", pageC.getContent());
	}

	@Test
	public void testRestorePageFromTrash() throws Exception {
		testRestorePageFromTrash(false);
	}

	@Test
	public void testRestorePageFromTrashWithExpando() throws Exception {
		testRestorePageFromTrash(true);
	}

	@Test
	public void testRevertPage() throws Exception {
		testRevertPage(false);
	}

	@Test
	public void testRevertPageWithExpando() throws Exception {
		testRevertPage(true);
	}

	protected void addExpandoValueToPage(WikiPage page) throws Exception {
		ExpandoValue value = ExpandoTestUtil.addValue(
			PortalUtil.getClassNameId(WikiPage.class), page.getPrimaryKey(),
			RandomTestUtil.randomString());

		ExpandoBridge expandoBridge = page.getExpandoBridge();

		ExpandoColumn column = value.getColumn();

		expandoBridge.addAttribute(
			column.getName(), ExpandoColumnConstants.STRING, value.getString());
	}

	protected void checkPopulatedServiceContext(
			ServiceContext serviceContext, WikiPage page,
			boolean hasExpandoValues)
		throws Exception {

		long[] assetCategoryIds = AssetCategoryLocalServiceUtil.getCategoryIds(
			WikiPage.class.getName(), page.getResourcePrimKey());

		Assert.assertArrayEquals(
			serviceContext.getAssetCategoryIds(), assetCategoryIds);

		AssetEntry assetEntry = AssetEntryLocalServiceUtil.getEntry(
			WikiPage.class.getName(), page.getResourcePrimKey());

		List<AssetLink> assetLinks = AssetLinkLocalServiceUtil.getLinks(
			assetEntry.getEntryId());

		long[] assetLinkEntryIds = ListUtil.toLongArray(
			assetLinks, AssetLink.ENTRY_ID2_ACCESSOR);

		Assert.assertArrayEquals(
			serviceContext.getAssetLinkEntryIds(), assetLinkEntryIds);

		String[] assetTagNames = AssetTagLocalServiceUtil.getTagNames(
			WikiPage.class.getName(), page.getResourcePrimKey());

		Assert.assertArrayEquals(
			serviceContext.getAssetTagNames(), assetTagNames);

		if (hasExpandoValues) {
			ExpandoBridge expandoBridge = page.getExpandoBridge();

			AssertUtils.assertEquals(
				expandoBridge.getAttributes(),
				serviceContext.getExpandoBridgeAttributes());
		}
	}

	protected List<WikiPage> createPageHierarchy(
			String parentTitle, int level, ServiceContext serviceContext)
		throws Exception {

		List<WikiPage> pages = new ArrayList<>();

		for (int i = 0; i < level; i++) {
			WikiPage page = WikiTestUtil.addPage(
				TestPropsValues.getUserId(), _node.getNodeId(),
				RandomTestUtil.randomString(), RandomTestUtil.randomString(),
				parentTitle, true, serviceContext);

			parentTitle = page.getTitle();

			pages.add(page);
		}

		return pages;
	}

	protected void testChangeParent(boolean hasExpandoValues) throws Exception {
		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		if (hasExpandoValues) {
			addExpandoValueToPage(page);
		}

		WikiPage parentPage = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.changeParent(
			TestPropsValues.getUserId(), _node.getNodeId(), page.getTitle(),
			parentPage.getTitle(), serviceContext);

		WikiPage retrievedPage = WikiPageLocalServiceUtil.getPage(
			page.getResourcePrimKey());

		checkPopulatedServiceContext(
			serviceContext, retrievedPage, hasExpandoValues);
	}

	protected void testRenamePage(boolean hasExpandoValues) throws Exception {
		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		if (hasExpandoValues) {
			addExpandoValueToPage(page);
		}

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		WikiPageLocalServiceUtil.renamePage(
			TestPropsValues.getUserId(), _node.getNodeId(), page.getTitle(),
			"New Title", true, serviceContext);

		WikiPage renamedPage = WikiPageLocalServiceUtil.getPage(
			_node.getNodeId(), "New Title");

		Assert.assertNotNull(renamedPage);

		checkPopulatedServiceContext(
			serviceContext, renamedPage, hasExpandoValues);
	}

	protected void testRestorePageFromTrash(boolean hasExpandoValues)
		throws Exception {

		WikiPage page = WikiTestUtil.addPage(
			_group.getGroupId(), _node.getNodeId(), true);

		if (hasExpandoValues) {
			addExpandoValueToPage(page);
		}

		page = WikiPageLocalServiceUtil.movePageToTrash(
			TestPropsValues.getUserId(), _node.getNodeId(), page.getTitle());

		WikiPageLocalServiceUtil.restorePageFromTrash(
			TestPropsValues.getUserId(), page);

		WikiPage restoredPage = WikiPageLocalServiceUtil.getPage(
			page.getResourcePrimKey());

		Assert.assertNotNull(restoredPage);

		if (hasExpandoValues) {
			ExpandoBridge expandoBridge = page.getExpandoBridge();

			ExpandoBridge restoredExpandoBridge =
				restoredPage.getExpandoBridge();

			AssertUtils.assertEquals(
				restoredExpandoBridge.getAttributes(),
				expandoBridge.getAttributes());
		}
	}

	protected void testRevertPage(boolean hasExpandoValues) throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		String originalContent = RandomTestUtil.randomString();

		WikiPage originalPage = WikiTestUtil.addPage(
			TestPropsValues.getUserId(), _node.getNodeId(),
			RandomTestUtil.randomString(), originalContent, true,
			serviceContext);

		if (hasExpandoValues) {
			addExpandoValueToPage(originalPage);
		}

		WikiPage updatedPage1 = WikiTestUtil.updatePage(
			originalPage, TestPropsValues.getUserId(),
			originalContent + "\nAdded second line.", serviceContext);

		Assert.assertNotEquals(originalContent, updatedPage1.getContent());

		WikiPage updatedPage2 = WikiTestUtil.updatePage(
			updatedPage1, TestPropsValues.getUserId(),
			updatedPage1.getContent() + "\nAdded third line.", serviceContext);

		Assert.assertNotEquals(originalContent, updatedPage2.getContent());

		WikiPage revertedPage = WikiPageLocalServiceUtil.revertPage(
			TestPropsValues.getUserId(), _node.getNodeId(),
			updatedPage2.getTitle(), originalPage.getVersion(), serviceContext);

		Assert.assertEquals(revertedPage.getContent(), originalContent);

		checkPopulatedServiceContext(
			serviceContext, revertedPage, hasExpandoValues);
	}

	@DeleteAfterTestRun
	private Group _group;

	private WikiNode _node;

}